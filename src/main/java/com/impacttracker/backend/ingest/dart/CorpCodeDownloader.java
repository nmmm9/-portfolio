package com.impacttracker.backend.ingest.dart;

import com.impacttracker.backend.config.OpendartProps;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.net.ssl.SSLException;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class CorpCodeDownloader {

    private static final Logger log = LoggerFactory.getLogger(CorpCodeDownloader.class);

    public record Corp(String corpCode, String corpName, String stockCode) {}

    private final OpendartProps props;
    private final WebClient web;
    private final Path cacheZip;
    private final Duration cacheTtl = Duration.ofDays(7);

    public CorpCodeDownloader(OpendartProps props) {
        this.props = props;
        System.setProperty("java.net.preferIPv4Stack", "true");

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(20))
                .secure(ssl -> {
                    try {
                        ssl.sslContext(SslContextBuilder.forClient().protocols("TLSv1.2").build());
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .compress(false)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        this.web = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();

        this.cacheZip = Path.of(System.getProperty("user.home"), ".sit", "corpCode.zip");
        try { Files.createDirectories(this.cacheZip.getParent()); } catch (IOException ignored) {}
    }

    public List<Corp> downloadAllCorps() {
        String urlPath = "/api/corpCode.xml";
        log.info("[corpCode] downloading from {} ...", props.getBaseUrl() + urlPath);

        byte[] body = null;
        try {
            body = web.get()
                    .uri(uri -> uri.path(urlPath)
                            .queryParam("crtfc_key", props.getApiKey())
                            .build())
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(40))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(15))
                            .filter(this::isTransient)
                            .doBeforeRetry(sig -> log.warn("[corpCode] retry {}/3 due to: {}",
                                    sig.totalRetries() + 1,
                                    String.valueOf(sig.failure()))))
                    .block();
        } catch (Throwable t) {
            log.warn("[corpCode] download error: {}", t.toString());
        }

        if (body == null || body.length == 0) {
            byte[] cached = readCacheIfFresh();
            if (cached != null) {
                log.warn("[corpCode] using cached ZIP due to download failure: {}", cacheZip);
                return parseFromBytes(cached);
            }
            throw new RuntimeException("Failed to download corpCode.xml and no usable cache");
        }

        if (isZip(body)) {
            writeCache(body);
            return parseFromBytes(body);
        } else if (startsWithXml(body)) {
            return parseFromBytes(body);
        } else {
            String head = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);
            byte[] cached = readCacheIfFresh();
            if (cached != null) {
                log.warn("[corpCode] unexpected response, falling back to cache. head={}", head.replaceAll("\\s+"," ").trim());
                return parseFromBytes(cached);
            }
            throw new RuntimeException("Unexpected response head: " + head.replaceAll("\\s+"," ").trim());
        }
    }

    private List<Corp> parseFromBytes(byte[] bytes) {
        if (isZip(bytes)) {
            byte[] xml = unzipSingle(bytes, "CORPCODE.xml");
            if (xml == null) xml = unzipByAnyXml(bytes);
            if (xml == null) throw new RuntimeException("CORPCODE.xml not found in zip");
            return parseCorpXml(xml);
        }
        return parseCorpXml(bytes);
    }

    private void writeCache(byte[] zipBytes) {
        try {
            Files.write(cacheZip, zipBytes);
        } catch (IOException e) {
            log.warn("[corpCode] cache write failed: {}", e.toString());
        }
    }

    private byte[] readCacheIfFresh() {
        try {
            if (Files.exists(cacheZip)) {
                Instant mtime = Files.getLastModifiedTime(cacheZip).toInstant();
                if (Instant.now().minus(cacheTtl).isBefore(mtime)) {
                    return Files.readAllBytes(cacheZip);
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private boolean isTransient(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            int s = wcre.getStatusCode().value();
            if (s >= 400 && s < 500 && s != 408) return false;
        }
        return ex instanceof java.net.SocketException
                || ex instanceof SSLException
                || ex instanceof EOFException
                || ex instanceof IOException
                || ex instanceof TimeoutException
                || ex.getClass().getName().contains("Timeout")
                || ex.getClass().getName().contains("Connection")
                || ex.getClass().getName().contains("Channel")
                || ex.getClass().getName().contains("Ssl")
                || ex.getClass().getName().contains("Handshake");
    }

    private boolean isZip(byte[] body) {
        return body.length >= 2 && (body[0] == 0x50 && body[1] == 0x4B);
    }

    private boolean startsWithXml(byte[] body) {
        String s = new String(body, 0, Math.min(body.length, 5), StandardCharsets.UTF_8);
        return s.startsWith("<?xml") || s.startsWith("<");
    }

    private byte[] unzipSingle(byte[] zipBytes, String targetName) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (e.getName().equalsIgnoreCase(targetName)) {
                    return zis.readAllBytes();
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("unzipSingle error: " + ex.getMessage(), ex);
        }
        return null;
    }

    private byte[] unzipByAnyXml(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (e.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    return zis.readAllBytes();
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("unzipByAnyXml error: " + ex.getMessage(), ex);
        }
        return null;
    }

    private List<Corp> parseCorpXml(byte[] xmlBytes) {
        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var doc = dbf.newDocumentBuilder().parse(is);
            var list = doc.getElementsByTagName("list");
            List<Corp> out = new ArrayList<>(list.getLength());
            for (int i = 0; i < list.getLength(); i++) {
                var node = list.item(i);
                var children = node.getChildNodes();
                String corpCode = null, corpName = null, stockCode = null;
                for (int j = 0; j < children.getLength(); j++) {
                    var c = children.item(j);
                    String name = c.getNodeName();
                    String val = c.getTextContent();
                    if ("corp_code".equals(name)) corpCode = val;
                    else if ("corp_name".equals(name)) corpName = val;
                    else if ("stock_code".equals(name)) stockCode = val;
                }
                if (corpCode != null && corpName != null) {
                    out.add(new Corp(corpCode.trim(), corpName.trim(), (stockCode == null ? "" : stockCode.trim())));
                }
            }
            log.info("[corpCode] loaded {} corps", out.size());
            return out;
        } catch (Exception ex) {
            throw new RuntimeException("parseCorpXml error: " + ex.getMessage(), ex);
        }
    }
}

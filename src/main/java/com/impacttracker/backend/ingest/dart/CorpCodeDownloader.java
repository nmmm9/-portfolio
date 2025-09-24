package com.impacttracker.backend.ingest.dart;

import com.impacttracker.backend.config.OpendartProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class CorpCodeDownloader {

    private static final Logger log = LoggerFactory.getLogger(CorpCodeDownloader.class);

    public record Corp(String corpCode, String corpName, String stockCode) {}

    private final OpendartProps props;
    private final WebClient web;

    public CorpCodeDownloader(OpendartProps props) {
        this.props = props;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        // 타임아웃/커넥션 설정
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        this.web = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    /** 전체 기업코드 ZIP/XML 파싱 (ZIP/직접 XML 모두 지원) */
    public List<Corp> downloadAllCorps() {
        String urlPath = "/api/corpCode.xml";
        log.info("[corpCode] downloading from {} ...", props.getBaseUrl() + urlPath);

        byte[] body = web.get()
                .uri(uri -> uri.path(urlPath)
                        .queryParam("crtfc_key", props.getApiKey())
                        .build())
                .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_XML, MediaType.ALL)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(35))
                // Conn reset/타임아웃 등에 대해 백오프 재시도
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(15))
                                .filter(ex -> isTransient(ex))
                )
                .block();

        if (body == null || body.length == 0) {
            throw new RuntimeException("Failed to download/parse corpCode.xml: empty body");
        }

        // magic check
        if (isZip(body)) {
            log.debug("[corpCode] received ZIP ({} bytes)", body.length);
            byte[] xml = unzipSingle(body, "CORPCODE.xml"); // 공식 명칭(대문자) 기준
            if (xml == null) {
                // 혹시 대소문자 다른 경우를 모두 탐색
                xml = unzipByAnyXml(body);
            }
            if (xml == null) {
                throw new RuntimeException("Failed to download/parse corpCode.xml: CORPCODE.xml not found in zip");
            }
            return parseCorpXml(xml);
        } else if (startsWithXml(body)) {
            log.debug("[corpCode] received XML ({} bytes)", body.length);
            return parseCorpXml(body);
        } else {
            // HTML 안내문(키오류/쿼터초과) 등일 수 있음
            String head = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);
            throw new RuntimeException("Failed to download/parse corpCode.xml: unexpected response head: " +
                    head.replaceAll("\\s+"," ").trim());
        }
    }

    private boolean isTransient(Throwable ex) {
        // 네트워크 오류/타임아웃 계열은 재시도
        return ex instanceof java.net.SocketException
                || ex instanceof java.io.IOException
                || ex instanceof java.util.concurrent.TimeoutException
                || ex.getClass().getName().contains("Timeout")
                || ex.getClass().getName().contains("Connection");
    }

    private boolean isZip(byte[] body) {
        return body.length >= 2 && (body[0] == 0x50 && body[1] == 0x4B); // 'P''K'
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

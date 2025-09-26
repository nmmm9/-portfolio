// src/main/java/com/impacttracker/backend/ingest/dart/DartApiClient.java
package com.impacttracker.backend.ingest.dart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impacttracker.backend.config.OpendartProps;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

import java.net.URI;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DartApiClient {

    private final WebClient web;
    private final String apiKey;
    private final ObjectMapper om = new ObjectMapper();

    public DartApiClient(OpendartProps props, WebClient.Builder builder) {
        this.apiKey = props.getApiKey();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(40))
                .compress(false)
                .keepAlive(true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(40, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(40, TimeUnit.SECONDS)));

        this.web = builder
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> {
                    String urlStr = request.url().toString();
                    StringBuilder uriB = new StringBuilder(urlStr);
                    if (StringUtils.hasText(apiKey) && !urlStr.contains("crtfc_key=")) {
                        uriB.append(urlStr.contains("?") ? "&" : "?")
                                .append("crtfc_key=").append(apiKey);
                    }
                    ClientRequest newReq = ClientRequest.from(request)
                            .url(URI.create(uriB.toString()))
                            .build();
                    return next.exchange(newReq);
                })
                .build();
    }

    @Getter
    @RequiredArgsConstructor
    public static class Report {
        private final String rcpNo;
        private final String reportName;
    }

    public List<Report> searchReports(String corpCode, YearMonth ym) {
        List<Report> out = new ArrayList<>();
        if (!StringUtils.hasText(corpCode)) return out;

        String yyyymm = ym.toString().replace("-", "");
        String bgnDe  = yyyymm + "01";
        String endDe  = yyyymm + String.format("%02d", ym.lengthOfMonth());

        int pageNo = 1;
        int pageCount = 100;
        int safetyPageLimit = 10;

        while (pageNo <= safetyPageLimit) {
            final int pn = pageNo;
            final int pc = pageCount;
            try {
                String json = web.get()
                        .uri(b -> b.path("/api/list.json")
                                .queryParam("corp_code", corpCode)
                                .queryParam("bgn_de", bgnDe)
                                .queryParam("end_de", endDe)
                                .queryParam("page_no", pn)
                                .queryParam("page_count", pc)
                                .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(40))
                        .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(20))
                                .jitter(0.4)
                                .filter(this::isTransient))
                        .block();

                if (json == null || json.isBlank()) {
                    log.warn("[dart][list] empty response corp={} ym={} page={}", corpCode, ym, pn);
                    break;
                }

                JsonNode root = om.readTree(json);
                String status  = optText(root, "status");
                String msg     = optText(root, "message");
                int totalCount = optInt(root, "total_count", -1);
                log.info("[dart][list] corp={} ym={} page={} status={} msg={} total={}",
                        corpCode, ym, pn, status, msg, totalCount);

                if ("020".equals(status)) {
                    throw new DartQuotaExceededException("OpenDART quota exceeded: " + String.valueOf(msg));
                }
                if (!"000".equals(status)) {
                    log.debug("[dart][list] non-OK status corp={} ym={} page={} status={} msg={}",
                            corpCode, ym, pn, status, msg);
                    break;
                }

                JsonNode list = root.get("list");
                if (list == null || !list.isArray() || list.size() == 0) break;

                for (JsonNode it : list) {
                    String rceptNo  = optText(it, "rcept_no");
                    String reportNm = optText(it, "report_nm");
                    if (StringUtils.hasText(rceptNo) && StringUtils.hasText(reportNm)) {
                        out.add(new Report(rceptNo, reportNm));
                    }
                }

                int gotSoFar = pn * pc;
                if (totalCount >= 0 && gotSoFar >= totalCount) break;
                pageNo++;
            } catch (DartQuotaExceededException q) {
                throw q; // 전체 중단 신호
            } catch (Exception e) {
                // ★ 트랜지언트면 예외로 올려서 상위에서 재시도
                if (isTransient(e)) {
                    throw new DartTransientException("Transient list.json error: " + e, e);
                }
                log.warn("[dart][list] request failed corp={} ym={} page={} cause={}",
                        corpCode, ym, pn, e.toString());
                break;
            }
        }

        log.debug("[dart][list] corp={} ym={} => {} reports", corpCode, ym, out.size());
        return out;
    }

    public String fetchDocumentXml(String rcpNo) {
        if (!StringUtils.hasText(rcpNo)) return null;
        try {
            return web.get()
                    .uri(b -> b.path("/api/document.xml").queryParam("rcept_no", rcpNo).build())
                    .accept(MediaType.APPLICATION_XML, MediaType.ALL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(40))
                    .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(20))
                            .jitter(0.4)
                            .filter(this::isTransient))
                    .block();
        } catch (Exception e) {
            if (isTransient(e)) {
                throw new DartTransientException("Transient document.xml error: " + e, e);
            }
            log.warn("[dart][doc] fetchDocumentXml failed rcpNo={} cause={}", rcpNo, e.toString());
            return null;
        }
    }

    private boolean isTransient(Throwable ex) {
        String n = ex.getClass().getName();
        return n.contains("Timeout")
                || n.contains("Connection")
                || n.contains("Channel")
                || n.contains("Ssl")
                || n.contains("Handshake")
                || ex instanceof java.net.SocketException
                || ex instanceof java.io.EOFException
                || ex instanceof java.io.IOException;
    }

    private static String optText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
    private static int optInt(JsonNode node, String field, int def) {
        if (node == null) return def;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asInt(def);
    }
}

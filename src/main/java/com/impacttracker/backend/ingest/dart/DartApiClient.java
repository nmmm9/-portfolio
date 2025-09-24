package com.impacttracker.backend.ingest.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impacttracker.backend.config.OpendartProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Component
public class DartApiClient {

    private static final Logger log = LoggerFactory.getLogger(DartApiClient.class);

    private final OpendartProps props;
    private final WebClient web;
    private final ObjectMapper om;

    private static final int PAGE_COUNT = 100;

    public DartApiClient(OpendartProps props) {
        this.props = props;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.web = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .exchangeStrategies(strategies)
                .build();

        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // 외부 모델
    public static class Report {
        private final String rcpNo;
        private final String reportName;
        private final String rceptDate;
        public Report(String rcpNo, String reportName, String rceptDate) {
            this.rcpNo = rcpNo;
            this.reportName = reportName;
            this.rceptDate = rceptDate;
        }
        public String rcpNo() { return rcpNo; }
        public String reportName() { return reportName; }
        public String rceptDate() { return rceptDate; }
        @Override public String toString() {
            return "Report{rcpNo=" + rcpNo + ", name=" + reportName + ", rceptDt=" + rceptDate + "}";
        }
    }

    /** 지정 기업/연월의 보고서 목록 조회(list.json 페이징) */
    public List<Report> searchReports(String corpCode, YearMonth ym) {
        String bgn = ym.atDay(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String end = ym.atEndOfMonth().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        List<Report> out = new ArrayList<>();
        int page = 1;
        StopWatch sw = new StopWatch("dart-list-" + corpCode + "-" + ym);

        while (true) {
            sw.start("page-" + page);
            ListJsonResponse res = callListJson(corpCode, bgn, end, page, PAGE_COUNT);
            sw.stop();

            if (!"000".equals(res.status)) {
                log.debug("[dart][list.json] corp={} ym={} status={} message={}", corpCode, ym, res.status, res.message);
                break;
            }

            if (res.list == null || res.list.isEmpty()) break;

            for (ListItem it : res.list) {
                String nm = safe(it.report_nm);
                if (isTargetReport(nm)) {
                    out.add(new Report(it.rcept_no, nm, it.rcept_dt));
                }
            }

            int total = parseIntSafe(res.total_count, 0);
            int got = page * PAGE_COUNT;
            if (got >= total) break;
            page++;
        }

        if (log.isDebugEnabled()) {
            log.debug("[dart][list] corp={} ym={} -> {} hits ({} ms)",
                    corpCode, ym, out.size(), sw.getTotalTimeMillis());
        }
        return out;
    }

    /** 문서 구조 XML(document.xml)을 그대로 문자열로 반환 */
    public String fetchDocumentXml(String rcpNo) {
        byte[] body = callBytesWithRetry(() ->
                web.get()
                        .uri(uri -> uri.path("/api/document.xml")
                                .queryParam("crtfc_key", props.getApiKey())
                                .queryParam("rcept_no", rcpNo)
                                .build())
                        .accept(MediaType.APPLICATION_XML)
                        .retrieve()
                        .bodyToMono(byte[].class)
        );

        if (body == null || body.length == 0) return "";
        String head = new String(body, 0, Math.min(body.length, 100), StandardCharsets.UTF_8);
        if (log.isDebugEnabled()) {
            log.debug("[dart][document.xml] rcpNo={} head='{}...'", rcpNo, head.replaceAll("\\s+"," ").trim());
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    // 내부: list.json 호출 + DTO
    private ListJsonResponse callListJson(String corpCode, String bgnDe, String endDe, int pageNo, int pageCount) {
        byte[] json = callBytesWithRetry(() ->
                web.get()
                        .uri(uri -> uri.path("/api/list.json")
                                .queryParam("crtfc_key", props.getApiKey())
                                .queryParam("corp_code", corpCode)
                                .queryParam("bgn_de", bgnDe)
                                .queryParam("end_de", endDe)
                                .queryParam("page_no", pageNo)
                                .queryParam("page_count", pageCount)
                                .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(byte[].class)
        );

        if (json == null || json.length == 0) {
            var r = new ListJsonResponse();
            r.status = "999"; r.message = "empty response";
            r.list = List.of(); r.total_count = "0";
            return r;
        }

        try {
            String s = new String(json, StandardCharsets.UTF_8);
            return om.readValue(s, ListJsonResponse.class);
        } catch (Exception ex) {
            String head = new String(json, 0, Math.min(json.length, 200), StandardCharsets.UTF_8);
            log.warn("[dart][list.json] parse fail corp={} page={} head='{}'", corpCode, pageNo, head.replaceAll("\\s+"," "));
            var r = new ListJsonResponse();
            r.status = "998"; r.message = "parse error: " + ex.getMessage();
            r.list = List.of(); r.total_count = "0";
            return r;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ListJsonResponse {
        public String status;
        public String message;
        public String page_no;
        public String total_count;
        public List<ListItem> list;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ListItem {
        public String corp_code;
        public String corp_name;
        public String stock_code;
        public String rcept_no;
        public String rcept_dt;
        @JsonProperty("report_nm")
        public String report_nm;
        public String flr_nm;
        public String rm;
    }

    // 유틸/필터
    private boolean isTargetReport(String name) {
        if (name == null) return false;
        String s = name.toLowerCase(Locale.ROOT);
        if (s.contains("사업보고서")) return true;
        if (s.contains("반기보고서")) return true;
        if (s.contains("분기보고서")) return true;
        if (s.contains("지속가능") || s.contains("sustainability")) return true;
        if (s.contains("esg")) return true;
        if (s.contains("csr")) return true;
        if (s.contains("사회공헌")) return true;
        if (s.contains("기부")) return true;
        if (s.contains("후원")) return true;
        return false;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static String safe(String s) { return s == null ? "" : s; }

    private byte[] callBytesWithRetry(Supplier<reactor.core.publisher.Mono<byte[]>> supplier) {
        try {
            return supplier.get()
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(300))
                            .maxBackoff(Duration.ofSeconds(3)))
                    .block(Duration.ofSeconds(30));
        } catch (Exception ex) {
            log.warn("[dart] request failed: {}", ex.toString());
            return null;
        }
    }
}

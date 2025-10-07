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

import java.math.BigDecimal;
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

    // ============================================
    // 기존 메서드들
    // ============================================

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
                throw q;
            } catch (Exception e) {
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

    // ============================================
    // ★★★ XBRL 재무제표 상세 API ★★★
    // ============================================

    /**
     * XBRL 재무제표 상세 조회 (판관비 상세 포함)
     * @param corpCode 법인코드
     * @param bsnsYear 사업연도
     * @param reprtCode 11011(사업보고서), 11012(반기), 11013(1분기), 11014(3분기)
     */
    public BigDecimal fetchDonationFromXbrl(String corpCode, int bsnsYear, String reprtCode) {
        try {
            String json = web.get()
                    .uri(b -> b.path("/api/fnlttXbrl.json")
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", bsnsYear)
                            .queryParam("reprt_code", reprtCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                            .filter(this::isTransient))
                    .block();

            if (json == null || json.isBlank()) return null;

            JsonNode root = om.readTree(json);
            String status = optText(root, "status");

            if (!"000".equals(status)) {
                log.debug("[dart][XBRL] status={} msg={}", status, optText(root, "message"));
                return null;
            }

            JsonNode list = root.get("list");
            if (list == null || !list.isArray()) return null;

            BigDecimal maxAmount = null;
            int foundCount = 0;

            for (JsonNode item : list) {
                String accountNm = optText(item, "account_nm");
                String labelKo = optText(item, "label_ko");
                String thstrmAmount = optText(item, "thstrm_amount");

                if (accountNm == null && labelKo == null) continue;
                if (thstrmAmount == null || thstrmAmount.isBlank()) continue;

                // 계정과목명 또는 라벨에서 기부/후원/사회공헌 키워드 찾기
                String name = (accountNm != null ? accountNm : "") + " " + (labelKo != null ? labelKo : "");

                if (name.contains("기부") ||
                        name.contains("후원") ||
                        name.contains("사회공헌") ||
                        name.contains("사회적공헌") ||
                        name.contains("출연") ||
                        name.contains("장학") ||
                        name.contains("성금") ||
                        name.contains("기탁")) {

                    try {
                        String cleaned = thstrmAmount.replace(",", "").replace("-", "").trim();
                        if (cleaned.isEmpty() || cleaned.equals("0")) continue;

                        BigDecimal amount = new BigDecimal(cleaned);
                        if (amount.signum() > 0) {
                            foundCount++;
                            log.debug("[dart][XBRL] 발견 #{}: {} = {}", foundCount, name.trim(), amount);
                            maxAmount = maxAmount == null ? amount : maxAmount.max(amount);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("[dart][XBRL] 숫자 변환 실패: {}", thstrmAmount);
                    }
                }
            }

            if (foundCount > 0) {
                log.info("[dart][XBRL] ✅ corp={} year={} report={} 발견={} 최대금액={}",
                        corpCode, bsnsYear, reprtCode, foundCount, maxAmount);
            } else {
                log.debug("[dart][XBRL] corp={} year={} report={} 기부금 항목 없음",
                        corpCode, bsnsYear, reprtCode);
            }

            return maxAmount;

        } catch (Exception e) {
            log.debug("[dart][XBRL] 조회 실패 corp={} year={} report={} cause={}",
                    corpCode, bsnsYear, reprtCode, e.toString());
            return null;
        }
    }

    /**
     * XBRL로 기부금 추출 (사업보고서 → 반기 → 1분기 → 3분기 순)
     */
    public BigDecimal fetchDonationFromXbrlAll(String corpCode, int year) {
        // 1) 사업보고서 (가장 상세함)
        BigDecimal amount = fetchDonationFromXbrl(corpCode, year, "11011");
        if (amount != null && amount.signum() > 0) {
            return amount;
        }

        // 2) 반기보고서
        amount = fetchDonationFromXbrl(corpCode, year, "11012");
        if (amount != null && amount.signum() > 0) {
            return amount;
        }

        // 3) 1분기보고서
        amount = fetchDonationFromXbrl(corpCode, year, "11013");
        if (amount != null && amount.signum() > 0) {
            return amount;
        }

        // 4) 3분기보고서
        amount = fetchDonationFromXbrl(corpCode, year, "11014");
        if (amount != null && amount.signum() > 0) {
            return amount;
        }

        return null;
    }

    /**
     * 재무제표 API에서 기부금/사회공헌비 추출
     * @param corpCode 법인코드
     * @param year 사업연도 (예: 2024)
     * @return 기부금액 (원 단위, null이면 없음)
     */
    public BigDecimal fetchDonationFromFinancial(String corpCode, int year) {
        // 1) 사업보고서 (11011)
        BigDecimal amount = fetchFinancialData(corpCode, year, "11011");
        if (amount != null && amount.signum() > 0) {
            log.info("[dart][재무제표] ✅ corp={} year={} 사업보고서 amount={}", corpCode, year, amount);
            return amount;
        }

        // 2) 반기보고서 (11012)
        amount = fetchFinancialData(corpCode, year, "11012");
        if (amount != null && amount.signum() > 0) {
            log.info("[dart][재무제표] ✅ corp={} year={} 반기보고서 amount={}", corpCode, year, amount);
            return amount;
        }

        log.debug("[dart][재무제표] 기부금 없음 corp={} year={}", corpCode, year);
        return null;
    }

    private BigDecimal fetchFinancialData(String corpCode, int year, String reprtCode) {
        try {
            String json = web.get()
                    .uri(b -> b.path("/api/fnlttSinglAcntAll.json")
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", year)
                            .queryParam("reprt_code", reprtCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                            .filter(this::isTransient))
                    .block();

            if (json == null) return null;

            JsonNode root = om.readTree(json);
            String status = optText(root, "status");

            if (!"000".equals(status)) {
                return null;
            }

            JsonNode list = root.get("list");
            if (list == null || !list.isArray()) return null;

            BigDecimal maxAmount = null;

            for (JsonNode item : list) {
                String accountNm = optText(item, "account_nm");
                String amountStr = optText(item, "thstrm_amount"); // 당기금액

                if (accountNm == null || amountStr == null) continue;

                // 기부/후원/사회공헌 관련 계정과목
                if (accountNm.contains("기부") ||
                        accountNm.contains("후원") ||
                        accountNm.contains("사회공헌") ||
                        accountNm.contains("사회적공헌") ||
                        accountNm.contains("출연") ||
                        accountNm.contains("장학")) {

                    try {
                        String cleaned = amountStr.replace(",", "").replace("-", "").trim();
                        if (cleaned.isEmpty()) continue;

                        BigDecimal amount = new BigDecimal(cleaned);
                        if (amount.signum() > 0) {
                            log.debug("[dart][재무제표] 발견: {} = {}", accountNm, amount);
                            maxAmount = maxAmount == null ? amount : maxAmount.max(amount);
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }

            return maxAmount;

        } catch (Exception e) {
            log.debug("[dart][재무제표] 조회 실패 corp={} year={} report={} cause={}",
                    corpCode, year, reprtCode, e.toString());
            return null;
        }
    }

    // ============================================
    // 유틸리티 메서드들
    // ============================================

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
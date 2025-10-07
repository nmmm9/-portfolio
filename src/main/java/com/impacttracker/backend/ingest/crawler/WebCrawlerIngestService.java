package com.impacttracker.backend.ingest.crawler;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.DonationParser;
import com.impacttracker.backend.service.KpiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹 크롤링 기반 데이터 수집 서비스
 * - DART 공시 페이지 직접 크롤링
 * - 기업 ESG 보고서 페이지 크롤링
 * - 기부금 정보 추출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebCrawlerIngestService {

    private final DonationParser donationParser;
    private final KpiService kpiService;

    // DART 공시 목록 URL 패턴
    private static final String DART_LIST_URL =
            "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=%s";

    // 기업 ESG 보고서 URL 패턴 (예시)
    private static final String ESG_REPORT_URL_PATTERN =
            "https://www.example.com/esg/%s/%d";

    /**
     * DART 공시 페이지 크롤링으로 기부금 정보 수집
     */
    public int crawlDartDisclosure(String corpCode, Organization org, YearMonth ym) {
        try {
            // 1. DART 검색 결과 페이지 크롤링
            List<String> rcpNos = searchDartReports(corpCode, ym);

            int saved = 0;
            for (String rcpNo : rcpNos) {
                try {
                    // 2. 개별 공시 페이지 크롤링
                    String content = crawlDartDocument(rcpNo);

                    // 3. 기부금 정보 파싱
                    BigDecimal amount = donationParser.extractDonationAmount(content);

                    if (amount != null && amount.signum() > 0) {
                        // 4. DB 저장
                        upsertKpiDonation(org, ym, amount, "CRAWLER:DART:" + rcpNo);
                        saved++;
                        log.info("[crawler] corp={} ym={} rcpNo={} amount={}",
                                corpCode, ym, rcpNo, amount);
                        break; // 첫 번째 유효한 데이터만 저장
                    }

                    // 크롤링 간격 (서버 부하 방지)
                    Thread.sleep(1000);

                } catch (Exception e) {
                    log.warn("[crawler] failed to crawl rcpNo={}: {}", rcpNo, e.getMessage());
                }
            }

            return saved;

        } catch (Exception e) {
            log.error("[crawler] failed to crawl DART for corp={} ym={}", corpCode, ym, e);
            return 0;
        }
    }

    /**
     * DART 검색 결과에서 접수번호 목록 추출
     */
    private List<String> searchDartReports(String corpCode, YearMonth ym) throws IOException {
        List<String> rcpNos = new ArrayList<>();

        // DART 검색 URL 구성
        String searchUrl = String.format(
                "https://dart.fss.or.kr/dsac001/search.ax?" +
                        "textCrpNm=%s&startDt=%s01&endDt=%s%02d&page=1",
                corpCode,
                ym.toString().replace("-", ""),
                ym.toString().replace("-", ""),
                ym.lengthOfMonth()
        );

        try {
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // 검색 결과 테이블에서 접수번호 추출
            Elements rows = doc.select("table.table-list tbody tr");

            Pattern reportPattern = Pattern.compile("(사업보고서|반기보고서|분기보고서)");

            for (Element row : rows) {
                String reportName = row.select("td:nth-child(3) a").text();

                // 관심 보고서만 필터링
                if (reportPattern.matcher(reportName).find()) {
                    String href = row.select("td:nth-child(3) a").attr("href");
                    String rcpNo = extractRcpNo(href);

                    if (rcpNo != null && !rcpNo.isEmpty()) {
                        rcpNos.add(rcpNo);
                    }
                }
            }

            log.info("[crawler] found {} reports for corp={} ym={}", rcpNos.size(), corpCode, ym);

        } catch (IOException e) {
            log.error("[crawler] failed to search DART: {}", e.getMessage());
            throw e;
        }

        return rcpNos;
    }

    /**
     * DART 공시 문서 내용 크롤링
     */
    private String crawlDartDocument(String rcpNo) throws IOException {
        String url = String.format(DART_LIST_URL, rcpNo);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            // 문서 본문 추출
            StringBuilder content = new StringBuilder();

            // 주요 테이블과 텍스트 영역 수집
            Elements tables = doc.select("table");
            for (Element table : tables) {
                content.append(table.text()).append("\n");
            }

            Elements divs = doc.select("div.section-body, div.xforms-body");
            for (Element div : divs) {
                content.append(div.text()).append("\n");
            }

            return content.toString();

        } catch (IOException e) {
            log.error("[crawler] failed to fetch document rcpNo={}: {}", rcpNo, e.getMessage());
            throw e;
        }
    }

    /**
     * 기업 ESG 보고서 페이지 크롤링
     */
    public int crawlEsgReport(String corpCode, Organization org, int year) {
        try {
            String url = String.format(ESG_REPORT_URL_PATTERN, corpCode, year);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // ESG 보고서에서 기부/봉사 정보 추출
            String content = doc.text();

            // 기부금 추출
            BigDecimal donation = donationParser.extractDonationAmount(content);

            // 봉사시간 추출
            BigDecimal volunteerHours = extractVolunteerHours(content);

            int saved = 0;
            YearMonth ym = YearMonth.of(year, 12); // 연간 데이터는 12월로 저장

            if (donation != null && donation.signum() > 0) {
                upsertKpiDonation(org, ym, donation, "CRAWLER:ESG");
                saved++;
            }

            if (volunteerHours != null && volunteerHours.signum() > 0) {
                upsertKpiVolunteer(org, ym, volunteerHours, "CRAWLER:ESG");
                saved++;
            }

            log.info("[crawler][ESG] corp={} year={} donation={} volunteer={}",
                    corpCode, year, donation, volunteerHours);

            return saved;

        } catch (Exception e) {
            log.error("[crawler][ESG] failed for corp={} year={}", corpCode, year, e);
            return 0;
        }
    }

    /**
     * 봉사시간 추출 (간단한 패턴 매칭)
     */
    private BigDecimal extractVolunteerHours(String text) {
        Pattern pattern = Pattern.compile("봉사\\s*[시활]?간?[:\\s]*([0-9,]+)\\s*시간");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                String numberStr = matcher.group(1).replace(",", "");
                return new BigDecimal(numberStr);
            } catch (NumberFormatException e) {
                log.debug("[crawler] failed to parse volunteer hours: {}", matcher.group(1));
            }
        }

        return null;
    }

    /**
     * URL에서 접수번호(rcpNo) 추출
     */
    private String extractRcpNo(String href) {
        if (href == null || href.isEmpty()) return null;

        Pattern pattern = Pattern.compile("rcpNo=([0-9]+)");
        Matcher matcher = pattern.matcher(href);

        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 기부금 KPI 저장
     */
    private void upsertKpiDonation(Organization org, YearMonth ym,
                                   BigDecimal amount, String source) {
        String periodYm = String.format("%04d%02d", ym.getYear(), ym.getMonthValue());

        kpiService.upsertMonthly(
                org.getId(),
                null,
                periodYm,
                KpiMetric.DONATION_AMOUNT_KRW,
                amount,
                source,
                false  // 크롤링 데이터는 검증 필요
        );
    }

    /**
     * 봉사시간 KPI 저장
     */
    private void upsertKpiVolunteer(Organization org, YearMonth ym,
                                    BigDecimal hours, String source) {
        String periodYm = String.format("%04d%02d", ym.getYear(), ym.getMonthValue());

        kpiService.upsertMonthly(
                org.getId(),
                null,
                periodYm,
                KpiMetric.VOLUNTEER_HOURS,
                hours,
                source,
                false  // 크롤링 데이터는 검증 필요
        );
    }

    /**
     * 네이버 뉴스 크롤링 (기업의 사회공헌 활동 뉴스)
     */
    public List<String> crawlNewsArticles(String companyName, YearMonth ym) {
        List<String> articles = new ArrayList<>();

        try {
            String query = companyName + " 기부 사회공헌";
            String searchUrl = "https://search.naver.com/search.naver?where=news&query=" +
                    java.net.URLEncoder.encode(query, "UTF-8");

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Elements newsItems = doc.select(".news_area");

            for (Element item : newsItems) {
                String title = item.select(".news_tit").text();
                String content = item.select(".news_dsc").text();

                articles.add(title + " " + content);

                if (articles.size() >= 10) break; // 최대 10개
            }

            log.info("[crawler][news] found {} articles for company={}", articles.size(), companyName);

        } catch (Exception e) {
            log.error("[crawler][news] failed: {}", e.getMessage());
        }

        return articles;
    }
}
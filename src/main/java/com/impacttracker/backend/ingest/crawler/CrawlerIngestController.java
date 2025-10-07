package com.impacttracker.backend.ingest.crawler;

import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.repo.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 웹 크롤링 기반 데이터 수집 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/ingest/crawler")
@RequiredArgsConstructor
public class CrawlerIngestController {

    private final OrganizationRepository orgRepo;
    private final WebCrawlerIngestService crawlerService;

    /**
     * 1) 단일 기업 DART 공시 크롤링
     * GET /ingest/crawler/dart?corp=00126380&ym=2024-03
     */
    @PostMapping("/dart")
    public Map<String, Object> crawlDart(@RequestParam String corp,
                                         @RequestParam String ym) {
        YearMonth yearMonth = parseYm(ym);
        Optional<Organization> orgOpt = orgRepo.findByCorpCode(corp);

        if (orgOpt.isEmpty()) {
            return Map.of("success", false, "message", "org not found for corp=" + corp);
        }

        int saved = crawlerService.crawlDartDisclosure(corp, orgOpt.get(), yearMonth);

        return Map.of(
                "success", true,
                "corp", corp,
                "ym", ym,
                "saved", saved
        );
    }

    /**
     * 2) 단일 기업 ESG 보고서 크롤링
     * POST /ingest/crawler/esg?corp=00126380&year=2023
     */
    @PostMapping("/esg")
    public Map<String, Object> crawlEsg(@RequestParam String corp,
                                        @RequestParam int year) {
        Optional<Organization> orgOpt = orgRepo.findByCorpCode(corp);

        if (orgOpt.isEmpty()) {
            return Map.of("success", false, "message", "org not found for corp=" + corp);
        }

        int saved = crawlerService.crawlEsgReport(corp, orgOpt.get(), year);

        return Map.of(
                "success", true,
                "corp", corp,
                "year", year,
                "saved", saved
        );
    }

    /**
     * 3) 뉴스 검색 (데이터 수집용)
     * GET /ingest/crawler/news?companyName=삼성전자&ym=2024-03
     */
    @GetMapping("/news")
    public Map<String, Object> searchNews(@RequestParam String companyName,
                                          @RequestParam String ym) {
        YearMonth yearMonth = parseYm(ym);

        List<String> articles = crawlerService.crawlNewsArticles(companyName, yearMonth);

        return Map.of(
                "success", true,
                "companyName", companyName,
                "ym", ym,
                "articlesFound", articles.size(),
                "articles", articles
        );
    }

    /**
     * 4) 배치 크롤링: 상위 N개 기업 대상
     * POST /ingest/crawler/batch?limit=10&ym=2024-03&mode=dart
     */
    @PostMapping("/batch")
    public Map<String, Object> crawlBatch(@RequestParam(defaultValue = "10") int limit,
                                          @RequestParam(required = false) String ym,
                                          @RequestParam(defaultValue = "dart") String mode) {
        YearMonth yearMonth = (ym == null || ym.isBlank())
                ? YearMonth.now()
                : parseYm(ym);

        List<Organization> targets = orgRepo.findAllDartEnabled();
        if (targets.isEmpty()) {
            return Map.of("success", false, "message", "no organizations found");
        }

        targets = targets.stream().limit(Math.max(1, limit)).toList();

        int total = 0;
        int success = 0;
        int failed = 0;

        for (Organization org : targets) {
            String corp = org.getCorpCode();
            if (corp == null || corp.isBlank()) continue;

            try {
                int saved = 0;

                if ("dart".equals(mode)) {
                    saved = crawlerService.crawlDartDisclosure(corp, org, yearMonth);
                } else if ("esg".equals(mode)) {
                    saved = crawlerService.crawlEsgReport(corp, org, yearMonth.getYear());
                }

                total += saved;
                if (saved > 0) success++;

                // 크롤링 간격 (서버 부하 방지 및 차단 방지)
                Thread.sleep(2000);

            } catch (Exception ex) {
                log.warn("[crawler][batch] skip corp={} cause={}", corp, ex.toString());
                failed++;
            }
        }

        return Map.of(
                "success", true,
                "mode", mode,
                "ym", yearMonth.toString(),
                "targets", targets.size(),
                "totalSaved", total,
                "successOrgs", success,
                "failedOrgs", failed
        );
    }

    /**
     * 5) 헬스체크
     * GET /ingest/crawler/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "WebCrawlerIngestService",
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 6) 크롤링 설정 조회
     * GET /ingest/crawler/config
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        config.put("timeout", 10000);
        config.put("delayBetweenRequests", 2000);
        config.put("maxRetries", 3);
        config.put("supportedModes", List.of("dart", "esg", "news"));

        return config;
    }

    /**
     * YearMonth 파싱 헬퍼
     */
    private YearMonth parseYm(String ym) {
        String s = ym.trim();
        if (s.matches("\\d{6}")) {
            return YearMonth.of(
                    Integer.parseInt(s.substring(0, 4)),
                    Integer.parseInt(s.substring(4, 6))
            );
        }
        return YearMonth.parse(s); // YYYY-MM
    }
}
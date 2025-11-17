package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.PositiveNews;
import com.socialimpact.tracker.repository.PositiveNewsRepository;
import com.socialimpact.tracker.service.PositiveNewsCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/positive-news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PositiveNewsController {

    private final PositiveNewsRepository positiveNewsRepository;
    private final PositiveNewsCollectorService collectorService;

    /**
     * POST /api/positive-news/collect
     * 긍정 뉴스 수집 (비동기)
     *
     * ⚠️ 기본값 변경: clearBefore = false (기존 뉴스 유지)
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectPositiveNews(
            @RequestParam(defaultValue = "2015") int fromYear,
            @RequestParam(defaultValue = "2025") int toYear,
            @RequestParam(defaultValue = "false") boolean clearBefore) {  // ← false로 변경!

        // 현재 수집 중인지 확인
        Map<String, Object> currentStatus = collectorService.getCollectionStatus();
        if ((Boolean) currentStatus.get("isCollecting")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "이미 수집 작업이 진행 중입니다.",
                    "status", "already_running",
                    "currentProgress", currentStatus.get("progress")
            ));
        }

        log.info("🚀 긍정 뉴스 수집 요청 ({} - {}) | DB 초기화: {}", fromYear, toYear, clearBefore);

        // 비동기 실행
        CompletableFuture.runAsync(() -> {
            try {
                collectorService.collectAllPositiveNews(fromYear, toYear, clearBefore);
                log.info("✅ 긍정 뉴스 수집 완료!");
            } catch (Exception e) {
                log.error("❌ 수집 작업 실패", e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", clearBefore
                        ? "기존 뉴스를 삭제하고 새로 수집을 시작했습니다."
                        : "기존 뉴스를 유지하면서 추가 수집을 시작했습니다.",
                "fromYear", fromYear,
                "toYear", toYear,
                "clearBefore", clearBefore,
                "status", "started"
        ));
    }

    /**
     * GET /api/positive-news/collect/status
     * 수집 진행 상태 조회
     */
    @GetMapping("/collect/status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus() {
        return ResponseEntity.ok(collectorService.getCollectionStatus());
    }

    /**
     * DELETE /api/positive-news/all
     * 모든 긍정 뉴스 삭제
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> deleteAllNews() {
        long count = positiveNewsRepository.count();
        positiveNewsRepository.deleteAll();

        log.info("🗑️ 전체 뉴스 삭제 완료: {} 건", count);

        return ResponseEntity.ok(Map.of(
                "message", "모든 긍정 뉴스가 삭제되었습니다.",
                "deletedCount", count
        ));
    }

    /**
     * GET /api/positive-news/organization/{orgId}
     * 특정 조직의 긍정 뉴스 목록 (페이지네이션 + 필터)
     */
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<Page<PositiveNews>> getNewsByOrganization(
            @PathVariable Long orgId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PositiveNews> news;

        if (year != null && category != null && !category.equals("all")) {
            news = positiveNewsRepository.findByOrganizationYearAndCategory(orgId, year, category, pageable);
        } else if (year != null) {
            news = positiveNewsRepository.findByOrganizationAndYear(orgId, year, pageable);
        } else if (category != null && !category.equals("all")) {
            news = positiveNewsRepository.findByOrganization_IdAndCategoryOrderByPublishedDateDesc(
                    orgId, category, pageable);
        } else {
            news = positiveNewsRepository.findByOrganization_IdOrderByPublishedDateDesc(orgId, pageable);
        }

        log.info("📰 조직 {} 뉴스 조회: {} 건 (page: {}, size: {})",
                orgId, news.getTotalElements(), page, size);

        return ResponseEntity.ok(news);
    }

    /**
     * GET /api/positive-news/organization/{orgId}/stats/by-year
     * 연도별 뉴스 개수 통계
     */
    @GetMapping("/organization/{orgId}/stats/by-year")
    public ResponseEntity<List<Map<String, Object>>> getYearlyStats(@PathVariable Long orgId) {
        List<Map<String, Object>> stats = positiveNewsRepository.countByOrganizationGroupByYear(orgId);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/positive-news/organization/{orgId}/stats/by-category
     * 카테고리별 뉴스 개수 통계
     */
    @GetMapping("/organization/{orgId}/stats/by-category")
    public ResponseEntity<List<Map<String, Object>>> getCategoryStats(@PathVariable Long orgId) {
        List<Map<String, Object>> stats = positiveNewsRepository.countByOrganizationGroupByCategory(orgId);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/positive-news/organization/{orgId}/stats/summary
     * 전체 통계 요약
     */
    @GetMapping("/organization/{orgId}/stats/summary")
    public ResponseEntity<Map<String, Object>> getStatsSummary(@PathVariable Long orgId) {
        Map<String, Object> stats = collectorService.getNewsStatistics(orgId);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/positive-news/organization/{orgId}/recent
     * 최근 뉴스 5개
     */
    @GetMapping("/organization/{orgId}/recent")
    public ResponseEntity<List<PositiveNews>> getRecentNews(@PathVariable Long orgId) {
        List<PositiveNews> news = positiveNewsRepository.findTop5ByOrganization_IdOrderByPublishedDateDesc(orgId);
        return ResponseEntity.ok(news);
    }

    /**
     * GET /api/positive-news/organization/{orgId}/count
     * 전체 뉴스 개수
     */
    @GetMapping("/organization/{orgId}/count")
    public ResponseEntity<Map<String, Long>> getTotalCount(@PathVariable Long orgId) {
        long count = positiveNewsRepository.countByOrganization_Id(orgId);
        return ResponseEntity.ok(Map.of("total", count));
    }

    /**
     * GET /api/positive-news/total-count
     * 전체 뉴스 개수
     */
    @GetMapping("/total-count")
    public ResponseEntity<Map<String, Long>> getTotalNewsCount() {
        long count = positiveNewsRepository.count();
        return ResponseEntity.ok(Map.of("total", count));
    }

    /**
     * GET /api/positive-news/{id}
     * 특정 뉴스 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<PositiveNews> getNewsById(@PathVariable Long id) {
        return positiveNewsRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/positive-news/{id}
     * 뉴스 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNews(@PathVariable Long id) {
        if (!positiveNewsRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        positiveNewsRepository.deleteById(id);
        log.info("🗑️ 뉴스 삭제: ID {}", id);

        return ResponseEntity.ok(Map.of(
                "message", "뉴스가 삭제되었습니다.",
                "deletedId", id.toString()
        ));
    }

    /**
     * DELETE /api/positive-news/organization/{orgId}/all
     * 특정 조직의 모든 뉴스 삭제
     */
    @DeleteMapping("/organization/{orgId}/all")
    public ResponseEntity<Map<String, Object>> deleteAllNewsByOrganization(@PathVariable Long orgId) {
        long count = positiveNewsRepository.countByOrganization_Id(orgId);
        positiveNewsRepository.deleteByOrganization_Id(orgId);

        log.info("🗑️ 조직 {} 뉴스 전체 삭제: {} 건", orgId, count);

        return ResponseEntity.ok(Map.of(
                "message", "조직의 모든 뉴스가 삭제되었습니다.",
                "organizationId", orgId,
                "deletedCount", count
        ));
    }

    /**
     * GET /api/positive-news/search
     * 키워드로 뉴스 검색
     */
    @GetMapping("/search")
    public ResponseEntity<Page<PositiveNews>> searchNews(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PositiveNews> news = positiveNewsRepository.searchByKeyword(keyword, pageable);

        log.info("🔍 키워드 '{}' 검색: {} 건", keyword, news.getTotalElements());

        return ResponseEntity.ok(news);
    }
}
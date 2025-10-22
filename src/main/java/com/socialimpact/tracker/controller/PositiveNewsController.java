package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.PositiveNews;
import com.socialimpact.tracker.repository.PositiveNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/positive-news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PositiveNewsController {

    private final PositiveNewsRepository positiveNewsRepository;

    /**
     * GET /api/positive-news/organization/{orgId}
     * 특정 조직의 긍정 뉴스 목록 (페이지네이션)
     */
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<Page<PositiveNews>> getNewsByOrganization(
            @PathVariable Long orgId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PositiveNews> news;

        if (year != null) {
            news = positiveNewsRepository.findByOrganizationAndYear(orgId, year, pageable);
        } else if (category != null && !category.equals("all")) {
            news = positiveNewsRepository.findByOrganization_IdAndCategoryOrderByPublishedDateDesc(
                    orgId, category, pageable);
        } else {
            news = positiveNewsRepository.findByOrganization_IdOrderByPublishedDateDesc(orgId, pageable);
        }

        log.info("📰 Retrieved {} news for organization {}", news.getContent().size(), orgId);
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
     * GET /api/positive-news/{id}
     * 특정 뉴스 조회
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
    public ResponseEntity<Void> deleteNews(@PathVariable Long id) {
        positiveNewsRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
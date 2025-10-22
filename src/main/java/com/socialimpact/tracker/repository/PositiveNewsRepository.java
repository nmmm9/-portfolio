package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.PositiveNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface PositiveNewsRepository extends JpaRepository<PositiveNews, Long> {

    // 특정 조직의 뉴스 (페이지네이션)
    Page<PositiveNews> findByOrganization_IdOrderByPublishedDateDesc(Long orgId, Pageable pageable);

    // 특정 조직의 특정 연도 뉴스
    @Query("SELECT p FROM PositiveNews p WHERE p.organization.id = :orgId AND YEAR(p.publishedDate) = :year ORDER BY p.publishedDate DESC")
    Page<PositiveNews> findByOrganizationAndYear(Long orgId, Integer year, Pageable pageable);

    // 특정 조직의 특정 카테고리 뉴스
    Page<PositiveNews> findByOrganization_IdAndCategoryOrderByPublishedDateDesc(Long orgId, String category, Pageable pageable);

    // 연도별 뉴스 개수 통계
    @Query("SELECT YEAR(p.publishedDate) as year, COUNT(p) as count " +
            "FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "GROUP BY YEAR(p.publishedDate) ORDER BY year DESC")
    List<Map<String, Object>> countByOrganizationGroupByYear(Long orgId);

    // 카테고리별 뉴스 개수 통계
    @Query("SELECT p.category as category, COUNT(p) as count " +
            "FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "GROUP BY p.category ORDER BY count DESC")
    List<Map<String, Object>> countByOrganizationGroupByCategory(Long orgId);

    // 전체 뉴스 개수
    long countByOrganization_Id(Long orgId);

    // URL 중복 확인
    boolean existsByUrl(String url);

    // 최근 뉴스
    List<PositiveNews> findTop5ByOrganization_IdOrderByPublishedDateDesc(Long orgId);
}
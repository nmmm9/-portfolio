package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.PositiveNews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Repository
public interface PositiveNewsRepository extends JpaRepository<PositiveNews, Long> {

    // ==================== 기본 조회 ====================

    /**
     * 특정 조직의 뉴스 전체 리스트
     */
    @Query("SELECT p FROM PositiveNews p WHERE p.organization = :organization")
    List<PositiveNews> findByOrganization(@Param("organization") com.socialimpact.tracker.entity.Organization organization);

    /**
     * 특정 조직의 뉴스 전체 (최신순)
     */
    Page<PositiveNews> findByOrganization_IdOrderByPublishedDateDesc(Long orgId, Pageable pageable);

    /**
     * 특정 조직의 특정 연도 뉴스
     */
    @Query("SELECT p FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "AND YEAR(p.publishedDate) = :year ORDER BY p.publishedDate DESC")
    Page<PositiveNews> findByOrganizationAndYear(@Param("orgId") Long orgId,
                                                 @Param("year") Integer year,
                                                 Pageable pageable);

    /**
     * 특정 조직의 특정 카테고리 뉴스
     */
    Page<PositiveNews> findByOrganization_IdAndCategoryOrderByPublishedDateDesc(
            Long orgId, String category, Pageable pageable);

    /**
     * 특정 조직의 특정 연도 + 카테고리 뉴스
     */
    @Query("SELECT p FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "AND YEAR(p.publishedDate) = :year AND p.category = :category " +
            "ORDER BY p.publishedDate DESC")
    Page<PositiveNews> findByOrganizationYearAndCategory(@Param("orgId") Long orgId,
                                                         @Param("year") Integer year,
                                                         @Param("category") String category,
                                                         Pageable pageable);

    // ==================== 통계 쿼리 ====================

    /**
     * 연도별 뉴스 개수 통계
     */
    @Query("SELECT YEAR(p.publishedDate) as year, COUNT(p) as count " +
            "FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "GROUP BY YEAR(p.publishedDate) ORDER BY year DESC")
    List<Map<String, Object>> countByOrganizationGroupByYear(@Param("orgId") Long orgId);

    /**
     * 카테고리별 뉴스 개수 통계
     */
    @Query("SELECT p.category as category, COUNT(p) as count " +
            "FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "GROUP BY p.category ORDER BY count DESC")
    List<Map<String, Object>> countByOrganizationGroupByCategory(@Param("orgId") Long orgId);

    /**
     * 전체 뉴스 개수
     */
    long countByOrganization_Id(Long orgId);

    /**
     * 최근 뉴스 5개
     */
    List<PositiveNews> findTop5ByOrganization_IdOrderByPublishedDateDesc(Long orgId);

    // ==================== 중복 확인 ====================

    /**
     * URL 중복 확인
     */
    boolean existsByUrl(String url);

    /**
     * 제목 중복 확인 (동일 조직 내)
     */
    boolean existsByOrganization_IdAndTitle(Long orgId, String title);

    // ==================== 검색 ====================

    /**
     * 키워드로 뉴스 검색 (제목 + 설명)
     */
    @Query("SELECT p FROM PositiveNews p WHERE " +
            "LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY p.publishedDate DESC")
    Page<PositiveNews> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 조직명으로 뉴스 검색
     */
    @Query("SELECT p FROM PositiveNews p WHERE " +
            "LOWER(p.organizationName) LIKE LOWER(CONCAT('%', :orgName, '%')) " +
            "ORDER BY p.publishedDate DESC")
    Page<PositiveNews> searchByOrganizationName(@Param("orgName") String orgName, Pageable pageable);

    // ==================== 삭제 ====================

    /**
     * 특정 조직의 모든 뉴스 삭제
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PositiveNews p WHERE p.organization.id = :orgId")
    void deleteByOrganization_Id(@Param("orgId") Long orgId);

    /**
     * 특정 연도의 뉴스 삭제
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PositiveNews p WHERE YEAR(p.publishedDate) = :year")
    void deleteByYear(@Param("year") Integer year);

    /**
     * 특정 조직의 특정 연도 뉴스 삭제
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "AND YEAR(p.publishedDate) = :year")
    void deleteByOrganizationAndYear(@Param("orgId") Long orgId, @Param("year") Integer year);

    // ==================== 고급 통계 ====================

    /**
     * 월별 뉴스 개수 (최근 12개월)
     */
    @Query(value = "SELECT " +
            "DATE_FORMAT(published_date, '%Y-%m') as month, " +
            "COUNT(*) as count " +
            "FROM positive_news " +
            "WHERE organization_id = :orgId " +
            "AND published_date >= DATE_SUB(NOW(), INTERVAL 12 MONTH) " +
            "GROUP BY DATE_FORMAT(published_date, '%Y-%m') " +
            "ORDER BY month DESC", nativeQuery = true)
    List<Map<String, Object>> countByOrganizationGroupByMonth(@Param("orgId") Long orgId);

    /**
     * 키워드별 뉴스 개수
     */
    @Query("SELECT p.matchedKeywords as keyword, COUNT(p) as count " +
            "FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "GROUP BY p.matchedKeywords ORDER BY count DESC")
    List<Map<String, Object>> countByOrganizationGroupByKeyword(@Param("orgId") Long orgId);

    /**
     * 전체 조직의 뉴스 통계 (상위 10개)
     */
    @Query("SELECT p.organizationName as organization, COUNT(p) as count " +
            "FROM PositiveNews p " +
            "GROUP BY p.organizationName " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getTopOrganizationsByNewsCount(Pageable pageable);

    /**
     * 특정 기간 내 뉴스 개수
     */
    @Query("SELECT COUNT(p) FROM PositiveNews p WHERE p.organization.id = :orgId " +
            "AND p.publishedDate BETWEEN :startDate AND :endDate")
    long countByOrganizationAndDateRange(@Param("orgId") Long orgId,
                                         @Param("startDate") java.time.LocalDate startDate,
                                         @Param("endDate") java.time.LocalDate endDate);
}
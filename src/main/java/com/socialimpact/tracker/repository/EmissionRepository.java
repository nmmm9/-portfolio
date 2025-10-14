package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.Emission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmissionRepository extends JpaRepository<Emission, Long> {

    /**
     * 연도별 배출량 조회
     */
    List<Emission> findByYear(Integer year);

    /**
     * 특정 조직의 특정 연도 배출량 조회 (합산용)
     */
    @Query("SELECT e FROM Emission e WHERE e.organization.id = :organizationId AND e.year = :year")
    Optional<Emission> findByOrganizationIdAndYear(
            @Param("organizationId") Long organizationId,
            @Param("year") Integer year
    );

    /**
     * 조직별 배출량 조회
     */
    @Query("SELECT e FROM Emission e WHERE e.organization.id = :organizationId")
    List<Emission> findByOrganizationId(@Param("organizationId") Long organizationId);

    /**
     * 연도 범위로 배출량 조회
     */
    @Query("SELECT e FROM Emission e WHERE e.year >= :fromYear AND e.year <= :toYear ORDER BY e.year DESC, e.organizationName ASC")
    List<Emission> findByYearBetween(@Param("fromYear") Integer fromYear, @Param("toYear") Integer toYear);

    /**
     * 범용 검색 쿼리
     * - orgId: 조직 ID (null이면 전체)
     * - fromYear: 시작 연도 (null이면 제한 없음)
     * - toYear: 종료 연도 (null이면 제한 없음)
     */
    @Query("SELECT e FROM Emission e WHERE " +
            "(:orgId IS NULL OR e.organization.id = :orgId) AND " +
            "(:fromYear IS NULL OR e.year >= :fromYear) AND " +
            "(:toYear IS NULL OR e.year <= :toYear) " +
            "ORDER BY e.year DESC, e.organizationName ASC")
    List<Emission> search(
            @Param("orgId") Long orgId,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear
    );

    /**
     * 특정 연도의 상위 배출 기업 조회 (배출량 많은 순)
     */
    @Query("SELECT e FROM Emission e WHERE e.year = :year ORDER BY e.totalEmissions DESC")
    List<Emission> findTopEmittersByYear(@Param("year") Integer year);

    /**
     * 특정 조직의 연도 범위 내 배출량 조회
     */
    @Query("SELECT e FROM Emission e WHERE e.organization.id = :orgId AND e.year >= :fromYear AND e.year <= :toYear ORDER BY e.year DESC")
    List<Emission> findByOrganizationAndYearRange(
            @Param("orgId") Long orgId,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear
    );

    /**
     * 연도별 총 배출량 합계 조회 (통계용)
     */
    @Query("SELECT e.year as year, SUM(e.totalEmissions) as total " +
            "FROM Emission e " +
            "WHERE (:orgId IS NULL OR e.organization.id = :orgId) AND " +
            "e.year >= :fromYear AND e.year <= :toYear " +
            "GROUP BY e.year ORDER BY e.year")
    List<Object[]> getAnnualEmissionsSummary(
            @Param("orgId") Long orgId,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear
    );

    /**
     * 전체 배출량 통계 조회
     */
    @Query("SELECT COUNT(e), SUM(e.totalEmissions), " +
            "COUNT(CASE WHEN e.verificationStatus = '검증완료' THEN 1 END) " +
            "FROM Emission e " +
            "WHERE (:orgId IS NULL OR e.organization.id = :orgId) AND " +
            "(:fromYear IS NULL OR e.year >= :fromYear) AND " +
            "(:toYear IS NULL OR e.year <= :toYear)")
    Object[] getEmissionsStatistics(
            @Param("orgId") Long orgId,
            @Param("fromYear") Integer fromYear,
            @Param("toYear") Integer toYear
    );
}
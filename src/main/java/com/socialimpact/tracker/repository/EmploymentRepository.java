package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.Employment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmploymentRepository extends JpaRepository<Employment, Long> {

    // 특정 조직의 고용 현황 조회
    List<Employment> findByOrganization_Id(Long orgId);

    // 특정 연도의 고용 현황 조회
    List<Employment> findByYear(Integer year);

    // 특정 조직의 특정 연도 고용 현황 조회
    Optional<Employment> findByOrganization_IdAndYear(Long orgId, Integer year);

    // 연도 목록 조회 (중복 제거)
    @Query("SELECT DISTINCT e.year FROM Employment e ORDER BY e.year DESC")
    List<Integer> findDistinctYears();

    // 전체 데이터 개수
    long count();

    // 특정 연도의 총 고용 인원 합계
    @Query("SELECT SUM(e.totalEmployees) FROM Employment e WHERE e.year = :year")
    Long sumTotalEmployeesByYear(Integer year);

    // 특정 연도의 평균 여성 비율
    @Query("SELECT AVG(CAST(e.femaleEmployees AS double) / NULLIF(e.totalEmployees, 0) * 100) " +
            "FROM Employment e WHERE e.year = :year AND e.totalEmployees > 0")
    Double avgFemaleRatioByYear(Integer year);

    // 전체 연도 조회 (오름차순)
    @Query("SELECT e FROM Employment e ORDER BY e.year ASC, e.organizationName ASC")
    List<Employment> findAllOrderByYearAsc();
}
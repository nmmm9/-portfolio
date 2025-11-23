package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.KpiReport;
import com.socialimpact.tracker.entity.KpiReport.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface KpiReportRepository extends JpaRepository<KpiReport, Long> {

    List<KpiReport> findByStatus(ReportStatus status);

    List<KpiReport> findByProjectId(Long projectId);

    @Query("SELECT r FROM KpiReport r WHERE r.reportDate BETWEEN :startDate AND :endDate")
    List<KpiReport> findByDateRange(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    @Query("SELECT r FROM KpiReport r WHERE r.status = :status ORDER BY r.approvedAt DESC")
    List<KpiReport> findRecentByStatus(@Param("status") ReportStatus status);

    @Query("SELECT COUNT(r) FROM KpiReport r WHERE r.status = 'APPROVED'")
    Long countApprovedReports();

    @Query("SELECT COUNT(r) FROM KpiReport r")
    Long countTotalReports();

    @Query("""
        SELECT r FROM KpiReport r 
        WHERE r.kpi.name = :kpiName 
        AND r.status = 'APPROVED' 
        AND r.reportDate BETWEEN :startDate AND :endDate
        """)
    List<KpiReport> findApprovedByKpiNameAndDateRange(
            @Param("kpiName") String kpiName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

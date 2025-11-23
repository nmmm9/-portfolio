package com.socialimpact.tracker.service;

import com.socialimpact.tracker.dto.*;
import com.socialimpact.tracker.entity.KpiReport;
import com.socialimpact.tracker.entity.KpiReport.ReportStatus;
import com.socialimpact.tracker.repository.DonationRepository;
import com.socialimpact.tracker.repository.KpiReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final KpiReportRepository kpiReportRepository;

    /**
     * 대시보드 전체 요약 데이터 조회
     */
    @Autowired
    private DonationRepository donationRepository; // ← 추가

    public DashboardSummaryDTO getDashboardSummary() {
        List<KpiReport> approvedReports = kpiReportRepository.findByStatus(ReportStatus.APPROVED);

        BigDecimal totalCo2 = sumByKpiName(approvedReports, "CO2 Emission Reduced");
        BigDecimal totalVolunteerHours = sumByKpiName(approvedReports, "Volunteer Hours");
        BigDecimal totalDonation = sumByKpiName(approvedReports, "Donation Amount");
        Long totalPeopleServed = sumByKpiName(approvedReports, "People Served").longValue();

        return new DashboardSummaryDTO(totalCo2, totalVolunteerHours, totalDonation, totalPeopleServed);
    }

    /**
     * Quick KPI - 최근 N개월 데이터
     */
    public QuickKpiDTO getQuickKpi(int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);

        List<KpiReport> reportsInRange = kpiReportRepository.findByDateRange(startDate, endDate);
        List<KpiReport> approvedReports = reportsInRange.stream()
                .filter(r -> r.getStatus() == ReportStatus.APPROVED)
                .toList();

        // 월별 데이터 집계
        List<QuickKpiDTO.MonthlyKpiData> monthlyData = groupByMonth(approvedReports);

        // 승인률 계산
        Long totalReports = kpiReportRepository.countTotalReports();
        Long approvedCount = kpiReportRepository.countApprovedReports();
        BigDecimal approvalRate = totalReports > 0
                ? BigDecimal.valueOf(approvedCount * 100.0 / totalReports).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 합계
        BigDecimal totalCo2 = sumByKpiName(approvedReports, "CO2 Emission Reduced");
        BigDecimal totalVolunteerHours = sumByKpiName(approvedReports, "Volunteer Hours");
        BigDecimal totalDonation = sumByKpiName(approvedReports, "Donation Amount");

        return new QuickKpiDTO(totalCo2, totalVolunteerHours, totalDonation, approvalRate, monthlyData);
    }

    /**
     * Impact Snapshot - 프로젝트별/카테고리별/지역별 임팩트
     */
    public ImpactSnapshotDTO getImpactSnapshot() {
        List<KpiReport> approvedReports = kpiReportRepository.findByStatus(ReportStatus.APPROVED);

        // 프로젝트별 임팩트
        List<ImpactSnapshotDTO.ProjectImpact> projectImpacts = approvedReports.stream()
                .collect(Collectors.groupingBy(r -> r.getProject().getName()))
                .entrySet().stream()
                .map(entry -> {
                    String projectName = entry.getKey();
                    List<KpiReport> reports = entry.getValue();
                    return new ImpactSnapshotDTO.ProjectImpact(
                            projectName,
                            sumByKpiName(reports, "CO2 Emission Reduced"),
                            sumByKpiName(reports, "Volunteer Hours"),
                            sumByKpiName(reports, "Donation Amount")
                    );
                })
                .collect(Collectors.toList());

        // 카테고리별 임팩트
        List<ImpactSnapshotDTO.CategoryImpact> categoryImpacts = approvedReports.stream()
                .collect(Collectors.groupingBy(r -> r.getProject().getCategory()))
                .entrySet().stream()
                .map(entry -> new ImpactSnapshotDTO.CategoryImpact(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(KpiReport::getValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        getColorForCategory(entry.getKey())
                ))
                .collect(Collectors.toList());

        // 지역별 임팩트 (샘플 데이터)
        List<ImpactSnapshotDTO.RegionImpact> regionImpacts = List.of(
                new ImpactSnapshotDTO.RegionImpact("Seoul", BigDecimal.valueOf(5000), 12),
                new ImpactSnapshotDTO.RegionImpact("Busan", BigDecimal.valueOf(3000), 8)
        );

        return new ImpactSnapshotDTO(projectImpacts, categoryImpacts, regionImpacts);
    }

    /**
     * 최근 활동 로그
     */
    public List<RecentActivityDTO> getRecentActivities(int limit) {
        List<KpiReport> recentReports = kpiReportRepository.findRecentByStatus(ReportStatus.APPROVED)
                .stream()
                .limit(limit)
                .toList();

        return recentReports.stream()
                .map(report -> new RecentActivityDTO(
                        report.getId(),
                        report.getProject().getName(),
                        report.getKpi().getName(),
                        report.getValue() + " " + report.getKpi().getUnit(),
                        report.getStatus().name(),
                        "APPROVED",
                        report.getApprovedAt(),
                        report.getApprovedBy()
                ))
                .collect(Collectors.toList());
    }

    // ===== 헬퍼 메서드 =====

    private BigDecimal sumByKpiName(List<KpiReport> reports, String kpiName) {
        return reports.stream()
                .filter(r -> r.getKpi().getName().equals(kpiName))
                .map(KpiReport::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<QuickKpiDTO.MonthlyKpiData> groupByMonth(List<KpiReport> reports) {
        Map<String, List<KpiReport>> byMonth = reports.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getReportDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                ));

        return byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new QuickKpiDTO.MonthlyKpiData(
                        entry.getKey(),
                        sumByKpiName(entry.getValue(), "CO2 Emission Reduced"),
                        sumByKpiName(entry.getValue(), "Volunteer Hours"),
                        sumByKpiName(entry.getValue(), "Donation Amount")
                ))
                .collect(Collectors.toList());
    }

    private String getColorForCategory(String category) {
        return switch (category) {
            case "Environment" -> "#10b981";
            case "Education" -> "#8b5cf6";
            case "Children" -> "#f59e0b";
            default -> "#6b7280";
        };
    }
}

package com.socialimpact.tracker.service;

import com.socialimpact.tracker.dto.ReportApprovalDTO;
import com.socialimpact.tracker.dto.ReportSubmitDTO;
import com.socialimpact.tracker.entity.*;
import com.socialimpact.tracker.entity.KpiReport.ReportStatus;
import com.socialimpact.tracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final KpiReportRepository kpiReportRepository;
    private final ProjectRepository projectRepository;
    private final KpiRepository kpiRepository;
    private final EvidenceRepository evidenceRepository;

    /**
     * 보고서 제출
     */
    public KpiReport submitReport(ReportSubmitDTO dto) {
        Project project = projectRepository.findById(dto.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        Kpi kpi = kpiRepository.findById(dto.getKpiId())
                .orElseThrow(() -> new RuntimeException("KPI not found"));

        KpiReport report = new KpiReport();
        report.setProject(project);
        report.setKpi(kpi);
        report.setValue(dto.getValue());
        report.setReportDate(dto.getReportDate());
        report.setStatus(ReportStatus.PENDING);

        KpiReport savedReport = kpiReportRepository.save(report);

        // 증빙 자료 저장
        if (dto.getEvidenceUrls() != null) {
            Arrays.stream(dto.getEvidenceUrls()).forEach(url -> {
                Evidence evidence = new Evidence();
                evidence.setReport(savedReport);
                evidence.setFileUrl(url);
                evidence.setFileType(getFileType(url));
                evidenceRepository.save(evidence);
            });
        }

        return savedReport;
    }

    /**
     * 보고서 승인/반려
     */
    public KpiReport approveOrRejectReport(ReportApprovalDTO dto) {
        KpiReport report = kpiReportRepository.findById(dto.getReportId())
                .orElseThrow(() -> new RuntimeException("Report not found"));

        ReportStatus newStatus = dto.getStatus().equalsIgnoreCase("APPROVED")
                ? ReportStatus.APPROVED
                : ReportStatus.REJECTED;

        report.setStatus(newStatus);
        report.setApprovedAt(LocalDateTime.now());
        report.setApprovedBy(dto.getApprovedBy());

        return kpiReportRepository.save(report);
    }

    /**
     * 상태별 보고서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<KpiReport> getReportsByStatus(String status) {
        ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
        return kpiReportRepository.findByStatus(reportStatus);
    }

    /**
     * 보고서 상세 조회
     */
    @Transactional(readOnly = true)
    public KpiReport getReportDetail(Long reportId) {
        return kpiReportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
    }

    private String getFileType(String url) {
        if (url.endsWith(".pdf")) return "PDF";
        if (url.endsWith(".xlsx") || url.endsWith(".xls")) return "EXCEL";
        if (url.endsWith(".png") || url.endsWith(".jpg")) return "IMAGE";
        return "UNKNOWN";
    }
}

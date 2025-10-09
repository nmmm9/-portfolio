package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.dto.ReportApprovalDTO;
import com.socialimpact.tracker.dto.ReportSubmitDTO;
import com.socialimpact.tracker.entity.KpiReport;
import com.socialimpact.tracker.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportService reportService;

    /**
     * POST /api/reports
     * 보고서 제출
     */
    @PostMapping
    public ResponseEntity<KpiReport> submitReport(@RequestBody ReportSubmitDTO dto) {
        KpiReport report = reportService.submitReport(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * PUT /api/reports/approval
     * 보고서 승인/반려
     */
    @PutMapping("/approval")
    public ResponseEntity<KpiReport> approveOrRejectReport(@RequestBody ReportApprovalDTO dto) {
        KpiReport report = reportService.approveOrRejectReport(dto);
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/reports?status=APPROVED
     * 상태별 보고서 목록
     */
    @GetMapping
    public ResponseEntity<List<KpiReport>> getReportsByStatus(
            @RequestParam(defaultValue = "APPROVED") String status) {
        List<KpiReport> reports = reportService.getReportsByStatus(status);
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/reports/{id}
     * 보고서 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<KpiReport> getReportDetail(@PathVariable Long id) {
        KpiReport report = reportService.getReportDetail(id);
        return ResponseEntity.ok(report);
    }
}
package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.dto.*;
import com.socialimpact.tracker.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/summary
     * 전체 KPI 합계 조회
     */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary() {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/dashboard/quick-kpi?period=12
     * 최근 N개월 KPI 추이
     */
    @GetMapping("/quick-kpi")
    public ResponseEntity<QuickKpiDTO> getQuickKpi(
            @RequestParam(defaultValue = "12") int period) {
        QuickKpiDTO quickKpi = dashboardService.getQuickKpi(period);
        return ResponseEntity.ok(quickKpi);
    }

    /**
     * GET /api/dashboard/impact-snapshot
     * 프로젝트별/카테고리별/지역별 임팩트
     */
    @GetMapping("/impact-snapshot")
    public ResponseEntity<ImpactSnapshotDTO> getImpactSnapshot() {
        ImpactSnapshotDTO snapshot = dashboardService.getImpactSnapshot();
        return ResponseEntity.ok(snapshot);
    }

    /**
     * GET /api/dashboard/recent-activities?limit=5
     * 최근 활동 로그
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<List<RecentActivityDTO>> getRecentActivities(
            @RequestParam(defaultValue = "5") int limit) {
        List<RecentActivityDTO> activities = dashboardService.getRecentActivities(limit);
        return ResponseEntity.ok(activities);
    }
}

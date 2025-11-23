package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.service.DartCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CompanyController {

    private final DartCollectorService dartCollectorService;

    /**
     * POST /api/companies/collect
     * DART API로 상장사 목록 수집
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectCompanies() {

        if (dartCollectorService.isCollecting()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "이미 수집 작업이 진행 중입니다.",
                    "status", "already_running"
            ));
        }

        log.info("🚀 상장사 목록 수집 시작");

        // 비동기 실행
        CompletableFuture.runAsync(() -> {
            try {
                dartCollectorService.collectAllListedCompanies();
                log.info("✅ 상장사 수집 완료!");
            } catch (Exception e) {
                log.error("❌ 수집 실패", e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "message", "상장사 수집을 시작했습니다.",
                "status", "started"
        ));
    }

    /**
     * GET /api/companies/collect/status
     * 수집 진행 상태 조회
     */
    @GetMapping("/collect/status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus() {
        Map<String, Object> status = Map.of(
                "isCollecting", dartCollectorService.isCollecting(),
                "totalCompanies", dartCollectorService.getTotalCompanies().get(),
                "processedCompanies", dartCollectorService.getProcessedCompanies().get(),
                "successCount", dartCollectorService.getSuccessCount().get(),
                "failureCount", dartCollectorService.getFailureCount().get(),
                "progressPercentage", dartCollectorService.getProgressPercentage()
        );

        return ResponseEntity.ok(status);
    }
}
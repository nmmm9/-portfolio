package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.dto.CollectionProgressDTO;
import com.socialimpact.tracker.service.DartCollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collection")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CollectionProgressController {

    private final DartCollectorService dartCollectorService;

    /**
     * GET /api/collection/progress
     * 현재 수집 진행률 조회
     */
    @GetMapping("/progress")
    public ResponseEntity<CollectionProgressDTO> getCollectionProgress() {
        CollectionProgressDTO progress = CollectionProgressDTO.builder()
                .isCollecting(dartCollectorService.isCollecting())
                .totalCompanies(dartCollectorService.getTotalCompanies().get())
                .processedCompanies(dartCollectorService.getProcessedCompanies().get())
                .successCount(dartCollectorService.getSuccessCount().get())
                .failureCount(dartCollectorService.getFailureCount().get())
                .progressPercentage(dartCollectorService.getProgressPercentage())
                .estimatedTimeRemaining(dartCollectorService.getEstimatedTimeRemaining())
                .elapsedTime((System.currentTimeMillis() - dartCollectorService.getStartTime()) / 1000)
                .build();

        return ResponseEntity.ok(progress);
    }

    /**
     * POST /api/collection/start
     * 수동으로 데이터 수집 시작
     */
    @PostMapping("/start")
    public ResponseEntity<String> startCollection() {
        if (dartCollectorService.isCollecting()) {
            return ResponseEntity.badRequest().body("Collection is already in progress");
        }

        // 백그라운드 스레드에서 실행
        new Thread(() -> {
            dartCollectorService.collectAllListedCompanies();
        }).start();

        return ResponseEntity.ok("Data collection started in background");
    }

    /**
     * GET /api/collection/status
     * 수집 상태 간단 조회
     */
    @GetMapping("/status")
    public ResponseEntity<String> getCollectionStatus() {
        if (dartCollectorService.isCollecting()) {
            return ResponseEntity.ok(String.format("Collecting: %.1f%% (%d/%d)",
                    dartCollectorService.getProgressPercentage(),
                    dartCollectorService.getProcessedCompanies().get(),
                    dartCollectorService.getTotalCompanies().get()));
        } else {
            return ResponseEntity.ok("Idle");
        }
    }
}
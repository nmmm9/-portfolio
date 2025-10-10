package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Emission;
import com.socialimpact.tracker.repository.EmissionRepository;
import com.socialimpact.tracker.service.GirCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/emissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GirController {

    private final GirCollectorService girCollectorService;
    private final EmissionRepository emissionRepository;

    /**
     * POST /api/emissions/upload
     * GIR 엑셀 파일 업로드 및 DB 저장
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadGirExcel(
            @RequestParam("file") MultipartFile file) {

        log.info("📤 Received GIR Excel file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "파일이 비어있습니다."));
        }

        if (!file.getOriginalFilename().endsWith(".xls") &&
                !file.getOriginalFilename().endsWith(".xlsx")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Excel 파일만 업로드 가능합니다."));
        }

        try {
            Map<String, Object> result = girCollectorService.processGirExcelFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error processing GIR Excel", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/emissions/statistics
     * 매칭 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = girCollectorService.getMatchingStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/emissions?year=2024
     * 연도별 배출량 조회
     */
    // 교체: year + orgId + fromYear + toYear 지원
    @GetMapping
    public ResponseEntity<List<Emission>> getEmissions(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false, name = "fromYear") Integer fromYear,
            @RequestParam(required = false, name = "toYear") Integer toYear) {

        if (year != null && orgId == null && fromYear == null && toYear == null) {
            // 기존 동작 유지 (year만 주어지면 기존 쿼리)
            return ResponseEntity.ok(emissionRepository.findByYear(year));
        }
        // 그 외에는 범용 검색
        List<Emission> rows = emissionRepository.search(orgId, fromYear, toYear);
        return ResponseEntity.ok(rows);
    }



    /**
     * GET /api/emissions/top-emitters?year=2024&limit=10
     * 상위 배출 기업 조회
     */
    @GetMapping("/top-emitters")
    public ResponseEntity<List<Emission>> getTopEmitters(
            @RequestParam Integer year,
            @RequestParam(defaultValue = "10") int limit) {

        List<Emission> topEmitters = emissionRepository
                .findTopEmittersByYear(year)
                .stream()
                .limit(limit)
                .toList();

        return ResponseEntity.ok(topEmitters);
    }

    /**
     * GET /api/emissions/organization/{orgId}
     * 특정 기업의 배출량 이력 조회
     */
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<List<Emission>> getEmissionsByOrganization(
            @PathVariable Long orgId) {

        List<Emission> emissions = emissionRepository.findByOrganizationId(orgId);
        return ResponseEntity.ok(emissions);
    }

    /**
     * DELETE /api/emissions/clear
     * 모든 배출량 데이터 삭제 (재업로드용)
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAllEmissions() {
        long count = emissionRepository.count();
        emissionRepository.deleteAll();
        log.info("🗑️  Deleted {} emission records", count);
        return ResponseEntity.ok("Deleted " + count + " records");
    }

    /**
     * GET /api/emissions/unmatched
     * 매칭 실패한 GIR 법인명 목록 조회
     */
    @GetMapping("/unmatched")
    public ResponseEntity<Map<String, Object>> getUnmatchedCompanies() {
        // 이 API는 업로드 중 실패한 기업 목록을 보여주는 용도
        // 실제로는 업로드 시 로그에 기록되므로, 여기서는 샘플 응답
        return ResponseEntity.ok(Map.of(
                "message", "Check server logs for unmatched companies during upload",
                "tip", "매칭 실패한 기업은 업로드 응답의 errors 필드에서 확인 가능"
        ));
    }

    /**
     * GET /api/emissions/matching-preview?girName=에스케이텔레콤
     * GIR 법인명으로 매칭될 Organization 미리보기
     */
    @GetMapping("/matching-preview")
    public ResponseEntity<Map<String, Object>> previewMatching(
            @RequestParam String girName) {

        try {
            // normalizeCompanyName 메서드 호출 테스트
            String normalized = normalizeForTest(girName);

            return ResponseEntity.ok(Map.of(
                    "girName", girName,
                    "normalized", normalized,
                    "message", "매칭 로직이 이 이름으로 검색합니다"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    private String normalizeForTest(String name) {
        String normalized = name;
        normalized = normalized.replace("SK", "에스케이")
                .replace("LG", "엘지")
                .replace("KT", "케이티")
                .replace("에스케이", "SK")
                .replace("엘지", "LG")
                .replace("케이티", "KT");
        normalized = normalized.replaceAll("[\\s()㈜(주)주식회사]", "").toLowerCase();
        return normalized;
    }
}
package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Emission;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.EmissionRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import com.socialimpact.tracker.service.GirCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/emissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GirController {

    private final GirCollectorService girCollectorService;
    private final EmissionRepository emissionRepository;
    private final OrganizationRepository organizationRepository;

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
     * GET /api/emissions?year=2024&orgId=1&fromYear=2020&toYear=2024
     * 배출량 조회 (다양한 필터 지원)
     */
    @GetMapping
    public ResponseEntity<List<Emission>> getEmissions(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false, name = "fromYear") Integer fromYear,
            @RequestParam(required = false, name = "toYear") Integer toYear) {

        log.info("🔍 Emissions query - year: {}, orgId: {}, fromYear: {}, toYear: {}",
                year, orgId, fromYear, toYear);

        if (year != null && orgId == null && fromYear == null && toYear == null) {
            return ResponseEntity.ok(emissionRepository.findByYear(year));
        }

        List<Emission> rows = emissionRepository.search(orgId, fromYear, toYear);
        log.info("✅ Found {} emission records", rows.size());
        return ResponseEntity.ok(rows);
    }

    /**
     * GET /api/emissions/organizations
     * 배출량 데이터가 있는 조직만 반환
     */
    @GetMapping("/organizations")
    public ResponseEntity<List<Map<String, Object>>> getOrganizationsWithEmissions() {
        log.info("🔍 Fetching organizations with emissions data");

        // 배출량 데이터가 있는 조직 ID들을 가져옴
        List<Long> orgIdsWithEmissions = emissionRepository.findAll()
                .stream()
                .map(e -> e.getOrganization().getId())
                .distinct()
                .collect(Collectors.toList());

        log.info("✅ Found {} organizations with emission data", orgIdsWithEmissions.size());

        // 해당 조직들의 정보를 가져옴
        List<Organization> organizations = organizationRepository.findAllById(orgIdsWithEmissions);

        // 간단한 Map 형태로 변환
        List<Map<String, Object>> result = organizations.stream()
                .map(org -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", org.getId());
                    map.put("name", org.getName());
                    map.put("type", org.getType() != null ? org.getType() : "");
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
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
}
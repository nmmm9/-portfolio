package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Employment;
import com.socialimpact.tracker.repository.EmploymentRepository;
import com.socialimpact.tracker.service.EmploymentCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/employments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmploymentController {

    private final EmploymentRepository employmentRepository;
    private final EmploymentCollectorService employmentCollectorService;

    /**
     * POST /api/employments/collect
     * 고용 현황 데이터 수집
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectEmployments(
            @RequestParam(defaultValue = "2023") int year) {

        log.info("🚀 Starting employment data collection for year {}", year);

        new Thread(() -> {
            try {
                employmentCollectorService.collectAllEmployments(year);
            } catch (Exception e) {
                log.error("❌ Collection failed", e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", "고용 현황 데이터 수집이 시작되었습니다.",
                "year", year,
                "status", "processing"
        ));
    }

    /**
     * POST /api/employments/collect/multi-year
     * 여러 연도 데이터 수집
     */
    @PostMapping("/collect/multi-year")
    public ResponseEntity<Map<String, Object>> collectMultipleYears(
            @RequestParam(defaultValue = "2021") int fromYear,
            @RequestParam(defaultValue = "2023") int toYear) {

        log.info("🚀 Starting multi-year employment data collection ({} - {})", fromYear, toYear);

        new Thread(() -> {
            try {
                employmentCollectorService.collectMultipleYears(fromYear, toYear);
            } catch (Exception e) {
                log.error("❌ Collection failed", e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", "여러 연도 고용 현황 데이터 수집이 시작되었습니다.",
                "fromYear", fromYear,
                "toYear", toYear,
                "status", "processing"
        ));
    }

    /**
     * GET /api/employments
     * 전체 고용 현황 조회
     */
    @GetMapping
    public ResponseEntity<List<Employment>> getAllEmployments() {
        List<Employment> employments = employmentRepository.findAll();
        log.info("📊 Retrieved {} employment records", employments.size());
        return ResponseEntity.ok(employments);
    }

    /**
     * GET /api/employments/{id}
     * 특정 고용 현황 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Employment> getEmploymentById(@PathVariable Long id) {
        return employmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/employments/organization/{orgId}
     * 특정 조직의 고용 현황 목록
     */
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<List<Employment>> getEmploymentsByOrganization(@PathVariable Long orgId) {
        List<Employment> employments = employmentRepository.findByOrganization_Id(orgId);
        return ResponseEntity.ok(employments);
    }

    /**
     * GET /api/employments/year/{year}
     * 특정 연도의 고용 현황 목록
     */
    @GetMapping("/year/{year}")
    public ResponseEntity<List<Employment>> getEmploymentsByYear(@PathVariable Integer year) {
        List<Employment> employments = employmentRepository.findByYear(year);
        return ResponseEntity.ok(employments);
    }

    /**
     * GET /api/employments/years
     * 고용 현황 데이터가 있는 연도 목록
     */
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        List<Integer> years = employmentRepository.findDistinctYears();
        return ResponseEntity.ok(years);
    }

    /**
     * GET /api/employments/statistics
     * 고용 현황 통계
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getEmploymentStatistics() {
        long totalRecords = employmentRepository.count();
        List<Integer> years = employmentRepository.findDistinctYears();

        Integer latestYear = years.isEmpty() ? null : years.get(0);
        Long totalEmployees = latestYear != null ?
                employmentRepository.sumTotalEmployeesByYear(latestYear) : 0L;
        Double avgFemaleRatio = latestYear != null ?
                employmentRepository.avgFemaleRatioByYear(latestYear) : 0.0;

        return ResponseEntity.ok(Map.of(
                "totalRecords", totalRecords,
                "availableYears", years,
                "yearCount", years.size(),
                "latestYear", latestYear != null ? latestYear : 0,
                "totalEmployees", totalEmployees != null ? totalEmployees : 0,
                "avgFemaleRatio", avgFemaleRatio != null ? avgFemaleRatio : 0.0
        ));
    }

    /**
     * DELETE /api/employments/{id}
     * 고용 현황 레코드 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployment(@PathVariable Long id) {
        employmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/employments/year/{year}
     * 특정 연도의 모든 고용 현황 데이터 삭제
     */
    @DeleteMapping("/year/{year}")
    public ResponseEntity<Map<String, Object>> deleteEmploymentsByYear(@PathVariable Integer year) {
        List<Employment> employments = employmentRepository.findByYear(year);
        int count = employments.size();
        employmentRepository.deleteAll(employments);

        return ResponseEntity.ok(Map.of(
                "deletedCount", count,
                "year", year
        ));
    }
}
package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Donation;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.DonationRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import com.socialimpact.tracker.service.DartCollectorService;
import com.socialimpact.tracker.service.DonationCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DonationController {

    private final DonationCollectorService donationCollectorService;
    private final DonationRepository donationRepository;
    private final DartCollectorService dartCollectorService;  // ← 이 줄 추가
    private final OrganizationRepository organizationRepository;  // ← 이 줄 추가

    /**
     * POST /api/donations/upload
     * 기부금 CSV 파일 업로드 (여러 파일 동시 업로드 가능)
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDonationFiles(
            @RequestParam("files") List<MultipartFile> files) {

        log.info("📤 Received {} donation CSV files", files.size());

        if (files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "파일이 없습니다."));
        }

        // CSV 파일만 허용
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null ||
                    (!filename.toLowerCase().endsWith(".csv") &&
                            !filename.toLowerCase().endsWith(".txt"))) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CSV 또는 TXT 파일만 업로드 가능합니다: " + filename));
            }
        }

        try {
            Map<String, Object> result = donationCollectorService.processDonationFiles(files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error processing donation files", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/donations
     * 전체 기부금 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Donation>> getAllDonations() {
        List<Donation> donations = donationRepository.findAll();
        return ResponseEntity.ok(donations);
    }

    /**
     * GET /api/donations/organization/{orgId}
     * 특정 조직의 기부금 목록
     */
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<List<Donation>> getDonationsByOrganization(@PathVariable Long orgId) {
        List<Donation> donations = donationRepository.findByOrganization_Id(orgId);
        return ResponseEntity.ok(donations);
    }

    /**
     * GET /api/donations/year/{year}
     * 특정 연도의 기부금 목록
     */
    @GetMapping("/year/{year}")
    public ResponseEntity<List<Donation>> getDonationsByYear(@PathVariable Integer year) {
        List<Donation> donations = donationRepository.findByYear(year);
        return ResponseEntity.ok(donations);
    }

    /**
     * GET /api/donations/years
     * 기부금 데이터가 있는 연도 목록
     */
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getAvailableYears() {
        List<Integer> years = donationRepository.findDistinctYears();
        return ResponseEntity.ok(years);
    }

    /**
     * GET /api/donations/statistics
     * 기부금 통계
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getDonationStatistics() {
        long totalDonations = donationRepository.count();
        List<Integer> years = donationRepository.findDistinctYears();

        return ResponseEntity.ok(Map.of(
                "totalRecords", totalDonations,
                "availableYears", years,
                "yearCount", years.size()
        ));
    }

    /**
     * DELETE /api/donations/{id}
     * 기부금 레코드 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDonation(@PathVariable Long id) {
        donationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/donations/year/{year}
     * 특정 연도의 모든 기부금 데이터 삭제
     */
    @DeleteMapping("/year/{year}")
    public ResponseEntity<Map<String, Object>> deleteDonationsByYear(@PathVariable Integer year) {
        List<Donation> donations = donationRepository.findByYear(year);
        int count = donations.size();
        donationRepository.deleteAll(donations);

        return ResponseEntity.ok(Map.of(
                "deletedCount", count,
                "year", year
        ));
    }
    /**
     * POST /api/donations/collect-single?corpCode=00164742
     * 단일 회사 테스트 수집
     */
    @PostMapping("/collect-single")
    public ResponseEntity<Map<String, Object>> collectSingleCompany(
            @RequestParam String corpCode) {
        log.info("🧪 단일 회사 테스트: {}", corpCode);

        dartCollectorService.collectDonationData(corpCode);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "corpCode", corpCode,
                "successCount", dartCollectorService.getSuccessCount().get(),
                "failureCount", dartCollectorService.getFailureCount().get()
        ));
    }

    @PostMapping("/collect-all")
    public ResponseEntity<Map<String, String>> collectAllDonations() {
        new Thread(() -> dartCollectorService.collectAllListedCompanies()).start();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "백그라운드에서 수집 시작됨"
        ));
    }

    @GetMapping("/collect-progress")
    public ResponseEntity<Map<String, Object>> getCollectProgress() {
        return ResponseEntity.ok(Map.of(
                "isCollecting", dartCollectorService.isCollecting(),
                "totalCompanies", dartCollectorService.getTotalCompanies().get(),
                "processedCompanies", dartCollectorService.getProcessedCompanies().get(),
                "successCount", dartCollectorService.getSuccessCount().get(),
                "failureCount", dartCollectorService.getFailureCount().get(),
                "progressPercentage", dartCollectorService.getProgressPercentage(),
                "estimatedTimeRemaining", dartCollectorService.getEstimatedTimeRemaining()
        ));
    }
    @PostMapping("/batch-import")
    public ResponseEntity<Map<String, Object>> batchImportDonations(
            @RequestBody List<Map<String, Object>> donationList) {

        int savedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        log.info("📥 일괄 임포트 시작: {}건", donationList.size());

        for (Map<String, Object> item : donationList) {
            try {
                String orgName = (String) item.get("organizationName");
                String stockCode = (String) item.get("stockCode");
                Integer year = (Integer) item.get("year");
                Integer quarter = (Integer) item.get("quarter");
                String reportType = (String) item.get("reportType");
                String dataSource = (String) item.get("dataSource");
                String verificationStatus = (String) item.get("verificationStatus");
                String currency = (String) item.get("currency");

                // amount 처리
                Object amountObj = item.get("donationAmount");
                Long amountLong;
                if (amountObj instanceof Integer) {
                    amountLong = ((Integer) amountObj).longValue();
                } else if (amountObj instanceof Long) {
                    amountLong = (Long) amountObj;
                } else {
                    amountLong = Long.parseLong(amountObj.toString());
                }
                BigDecimal amount = new BigDecimal(amountLong);

                // Organization 찾기 또는 생성
                Organization org = organizationRepository.findAll().stream()
                        .filter(o -> o.getName().equals(orgName))
                        .findFirst()
                        .orElseGet(() -> {
                            Organization newOrg = new Organization();
                            newOrg.setName(orgName);
                            newOrg.setType("상장사");
                            Organization saved = organizationRepository.save(newOrg);
                            log.info("  🏢 새 조직 생성: {}", orgName);
                            return saved;
                        });

                // Donation 중복 체크 및 저장
                Donation donation = donationRepository
                        .findByOrganization_IdAndYearAndQuarter(
                                org.getId(), year, quarter)
                        .orElse(new Donation());

                donation.setOrganization(org);
                donation.setOrganizationName(orgName);
                donation.setStockCode(stockCode);
                donation.setYear(year);
                donation.setQuarter(quarter);
                donation.setDonationAmount(amount);
                donation.setReportType(reportType);
                donation.setDataSource(dataSource != null ? dataSource : "TXT_FILE");
                donation.setVerificationStatus(verificationStatus != null ? verificationStatus : "자동수집");
                donation.setCurrency(currency != null ? currency : "KRW");

                donationRepository.save(donation);
                savedCount++;

                log.debug("  ✅ {} {}년: {:,}원", orgName, year, amount);

            } catch (Exception e) {
                failedCount++;
                String error = String.format("처리 실패: %s - %s",
                        item.get("organizationName"), e.getMessage());
                errors.add(error);
                log.warn("  ❌ {}", error);
            }
        }

        log.info("✅ 임포트 완료: 성공 {}, 실패 {}", savedCount, failedCount);

        Map<String, Object> result = new HashMap<>();
        result.put("total", donationList.size());
        result.put("savedCount", savedCount);
        result.put("failedCount", failedCount);

        if (!errors.isEmpty()) {
            result.put("errors", errors.subList(0, Math.min(10, errors.size())));
        }

        return ResponseEntity.ok(result);
    }
}
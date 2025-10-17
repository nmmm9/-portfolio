package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.entity.Donation;
import com.socialimpact.tracker.repository.DonationRepository;
import com.socialimpact.tracker.service.DonationCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "CSV 파일만 업로드 가능합니다: " + filename));
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
}
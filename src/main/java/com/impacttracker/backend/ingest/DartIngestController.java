package com.impacttracker.backend.ingest;

import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.repo.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/ingest/dart")
@RequiredArgsConstructor
public class DartIngestController {

    private final OrganizationRepository orgRepo;
    private final DartIngestService dartIngestService;

    /** 단일 기업 연도별 수집 */
    @PostMapping("/one")
    public String ingestOne(@RequestParam String corp, @RequestParam int year) {
        Optional<Organization> orgOpt = orgRepo.findByCorpCode(corp);
        if (orgOpt.isEmpty()) return "org not found for corp=" + corp;

        int saved = dartIngestService.ingestDonationForYear(corp, orgOpt.get(), year);
        return "saved=" + saved;
    }

    /** orgId 기반 수집 */
    @PostMapping("/org")
    public String ingestByOrg(@RequestParam Long orgId, @RequestParam int year) {
        Optional<Organization> orgOpt = orgRepo.findById(orgId);
        if (orgOpt.isEmpty()) return "org not found id=" + orgId;
        String corp = orgOpt.get().getCorpCode();
        if (corp == null || corp.isBlank()) return "no corpCode on org id=" + orgId;

        int saved = dartIngestService.ingestDonationForYear(corp, orgOpt.get(), year);
        return "saved=" + saved;
    }

    /** 간단 배치: 상위 N개 조직 대상으로 특정 연도 수집 */
    @PostMapping("/batch")
    public String ingestBatch(@RequestParam(defaultValue = "10") int limit,
                              @RequestParam(required = false) Integer year) {
        if (year == null) year = java.time.Year.now().getValue();

        var targets = orgRepo.findAllDartEnabled();
        if (targets.isEmpty()) return "no dart-enabled organizations";
        targets = targets.stream().limit(Math.max(1, limit)).toList();

        int total = 0;
        for (Organization org : targets) {
            String corp = org.getCorpCode();
            if (corp == null || corp.isBlank()) continue;
            try {
                int saved = dartIngestService.ingestDonationForYear(corp, org, year);
                total += saved;
            } catch (Exception ex) {
                log.warn("[ingest][one] skip corp={} year={} cause={}", corp, year, ex.toString());
            }
        }
        return "batch saved=" + total + " (year=" + year + ", limit=" + targets.size() + ")";
    }
}
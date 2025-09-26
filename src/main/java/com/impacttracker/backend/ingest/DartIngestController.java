package com.impacttracker.backend.ingest;

import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartApiClient;
import com.impacttracker.backend.repo.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/ingest/dart")
@RequiredArgsConstructor
public class DartIngestController {

    private final OrganizationRepository orgRepo;
    private final DartIngestService dartIngestService;
    private final DartApiClient dart;

    /** 0) 핑(네트워크/키 확인): 특정 corp+월로 list.json 호출만 해봄 */
    @GetMapping("/ping")
    public String ping(@RequestParam String corp, @RequestParam String ym) {
        YearMonth y = parseYm(ym);
        var list = dart.searchReports(corp, y);
        return "reports=" + list.size();
    }

    /** 1) 단일 기업+월 수집 */
    @PostMapping("/one")
    public String ingestOne(@RequestParam String corp, @RequestParam String ym) {
        YearMonth y = parseYm(ym);
        Optional<Organization> orgOpt = orgRepo.findByCorpCode(corp);
        if (orgOpt.isEmpty()) return "org not found for corp=" + corp;

        int saved = dartIngestService.ingestDonationForCorp(corp, orgOpt.get(), y);
        return "saved=" + saved;
    }

    /** 2) orgId 기반으로도 실행 */
    @PostMapping("/org")
    public String ingestByOrg(@RequestParam Long orgId, @RequestParam String ym) {
        YearMonth y = parseYm(ym);
        Optional<Organization> orgOpt = orgRepo.findById(orgId);
        if (orgOpt.isEmpty()) return "org not found id=" + orgId;
        String corp = orgOpt.get().getCorpCode();
        if (corp == null || corp.isBlank()) return "no corpCode on org id=" + orgId;

        int saved = dartIngestService.ingestDonationForCorp(corp, orgOpt.get(), y);
        return "saved=" + saved;
    }

    /** 3) 간단 배치: 상위 N개 조직 대상으로 당월 하나만 돌리기 */
    @PostMapping("/batch")
    public String ingestBatch(@RequestParam(defaultValue = "10") int limit,
                              @RequestParam(required = false) String ym) {
        YearMonth y = (ym == null || ym.isBlank()) ? YearMonth.now() : parseYm(ym);
        List<Organization> targets = orgRepo.findAllDartEnabled();
        if (targets.isEmpty()) return "no dart-enabled organizations";
        targets = targets.stream().limit(Math.max(1, limit)).toList();

        int total = 0;
        for (Organization org : targets) {
            String corp = org.getCorpCode();
            if (corp == null || corp.isBlank()) continue;
            try {
                int saved = dartIngestService.ingestDonationForCorp(corp, org, y);
                total += saved;
            } catch (Exception ex) {
                log.warn("[ingest][one] skip corp={} cause={}", corp, ex.toString());
            }
        }
        return "batch saved=" + total + " (ym=" + y + ", limit=" + targets.size() + ")";
    }

    private static YearMonth parseYm(String ym) {
        // 허용: YYYY-MM 또는 YYYYMM
        String s = ym.trim();
        if (s.matches("\\d{6}")) {
            return YearMonth.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)));
        }
        return YearMonth.parse(s); // 2025-09
    }
}

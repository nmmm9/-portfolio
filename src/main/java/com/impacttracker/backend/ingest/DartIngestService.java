// src/main/java/com/impacttracker/backend/ingest/DartIngestService.java
package com.impacttracker.backend.ingest;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartApiClient;
import com.impacttracker.backend.ingest.dart.DartQuotaExceededException;
import com.impacttracker.backend.ingest.dart.DartTransientException;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DartIngestService {
    private static final Logger log = LoggerFactory.getLogger(DartIngestService.class);

    private final DartApiClient dart;
    private final DonationParser donationParser;
    private final KpiMonthlyRepository kpiRepo;

    private static final Pattern ALLOW_REPORT = Pattern.compile("(사업보고서|반기보고서|분기보고서)");

    public DartIngestService(DartApiClient dart,
                             DonationParser donationParser,
                             KpiMonthlyRepository kpiRepo) {
        this.dart = dart;
        this.donationParser = donationParser;
        this.kpiRepo = kpiRepo;
    }

    public int ingestDonationForCorp(String corpCode, Organization org, YearMonth ym) {
        // === 1) list.json 재시도 (최대 3회) ===
        List<DartApiClient.Report> all = null;
        int listTry = 0;
        while (listTry < 3) {
            listTry++;
            try {
                all = dart.searchReports(corpCode, ym);
                break; // 성공
            } catch (DartQuotaExceededException q) {
                throw q; // 전체 중단
            } catch (DartTransientException te) {
                backoff(listTry);
                if (listTry >= 3) {
                    log.warn("[DART] list.json transient fail 3/3 corp={} ym={} cause={}", corpCode, ym, te.toString());
                }
            }
        }
        if (all == null || all.isEmpty()) {
            log.debug("[DART] corp={} ym={} reports=0", corpCode, ym);
            return 0;
        }

        // 허용 보고서만 선별(사업/반기/분기)
        List<DartApiClient.Report> reports = all.stream()
                .filter(r -> r.getReportName() != null && ALLOW_REPORT.matcher(r.getReportName()).find())
                .sorted(Comparator.comparing((DartApiClient.Report r) -> rank(r.getReportName())))
                .toList();
        if (reports.isEmpty()) {
            log.debug("[DART] corp={} ym={} only non-allowed reports (size={})", corpCode, ym, all.size());
            return 0;
        }

        // === 2) document.xml 재시도 (리포트별 최대 3회) ===
        int saved = 0;
        for (var r : reports) {
            String xml = null;
            int docTry = 0;
            while (docTry < 3) {
                docTry++;
                try {
                    xml = dart.fetchDocumentXml(r.getRcpNo());
                    break; // 성공 or null
                } catch (DartTransientException te) {
                    backoff(docTry);
                    if (docTry >= 3) {
                        log.warn("[DART] doc.xml transient fail 3/3 corp={} ym={} rcpNo={} cause={}",
                                corpCode, ym, r.getRcpNo(), te.toString());
                    }
                }
            }
            if (xml == null || xml.isBlank()) {
                log.debug("[DART] corp={} ym={} rcpNo={} empty xml", corpCode, ym, r.getRcpNo());
                continue;
            }

            BigDecimal amount = donationParser.extractDonationAmount(xml);
            log.debug("[DART] corp={} ym={} report='{}' rcpNo={} amount={}",
                    corpCode, ym, r.getReportName(), r.getRcpNo(), amount);

            if (amount != null && amount.signum() > 0) {
                boolean ok = upsertKpiDonation(
                        org, ym.getYear(), ym.getMonthValue(),
                        amount, "DART:" + r.getRcpNo()
                );
                log.info("[ingest][DART] corp={} ym={} report='{}' saved={}",
                        corpCode, ym, r.getReportName(), ok ? 1 : 0);
                if (ok) saved++;
                break; // 첫 성공만 저장
            } else {
                log.debug("[parse] corp={} ym={} rcpNo={} (no donation match)", corpCode, ym, r.getRcpNo());
            }
        }
        return saved;
    }

    private static int rank(String name) {
        if (name == null) return 9;
        if (name.contains("사업보고서")) return 0;
        if (name.contains("반기보고서")) return 1;
        if (name.contains("분기보고서")) return 2;
        return 9;
    }

    private void backoff(int attempt) {
        long base = 500L * attempt;              // 0.5s, 1.0s, 1.5s
        long jitter = (long)(Math.random() * 300); // +0~300ms
        try { Thread.sleep(Math.min(2000, base + jitter)); } catch (InterruptedException ignored) {}
    }

    private boolean upsertKpiDonation(Organization org, int year, int month, BigDecimal value, String source) {
        String periodYm = toYm(year, month);
        Long orgId = org.getId();
        KpiMetric metric = KpiMetric.DONATION_AMOUNT_KRW;

        Optional<KpiMonthly> existing = kpiRepo.findFirstByOrgIdAndProjectIdAndPeriodYmAndMetric(
                orgId, null, periodYm, metric
        );

        if (existing.isPresent()) {
            KpiMonthly k = existing.get();
            boolean needUpdate =
                    (k.getValue() == null || k.getValue().compareTo(value) != 0) ||
                            (k.getSource() == null ? source != null : !k.getSource().equals(source));
            if (needUpdate) {
                k.setValue(value == null ? BigDecimal.ZERO : value);
                k.setSource(source);
                kpiRepo.save(k);
            }
            return false;
        } else {
            KpiMonthly k = new KpiMonthly();
            k.setOrgId(orgId);
            k.setProjectId(null);
            k.setPeriodYm(periodYm);
            k.setMetric(metric);
            k.setValue(value == null ? BigDecimal.ZERO : value);
            k.setSource(source);
            k.setApproved(true);
            kpiRepo.save(k);
            return true;
        }
    }

    private String toYm(int year, int month) {
        String m = (month < 10) ? ("0" + month) : Integer.toString(month);
        return year + "-" + m;
    }
}

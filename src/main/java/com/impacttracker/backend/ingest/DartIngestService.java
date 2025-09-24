package com.impacttracker.backend.ingest;

import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartApiClient;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
public class DartIngestService {
    private static final Logger log = LoggerFactory.getLogger(DartIngestService.class);

    private final DartApiClient dart;
    private final DonationParser donationParser;
    private final KpiMonthlyRepository kpiRepo;

    public DartIngestService(DartApiClient dart, DonationParser donationParser, KpiMonthlyRepository kpiRepo) {
        this.dart = dart;
        this.donationParser = donationParser;
        this.kpiRepo = kpiRepo;
    }

    public int ingestDonationForCorp(String corpCode, Organization org, YearMonth ym) {
        List<DartApiClient.Report> reports = dart.searchReports(corpCode, ym);
        int saved = 0;

        for (var r : reports) {
            String xml = dart.fetchDocumentXml(r.rcpNo());
            BigDecimal amount = donationParser.extractDonationAmount(xml);

            if (amount != null && amount.signum() >= 0) {
                boolean ok = upsertKpiDonation(org, ym.getYear(), ym.getMonthValue(), amount, "DART:" + r.rcpNo());
                if (ok) saved++;
                log.info("[ingest][DART] {} {} {} saved={}", corpCode, ym, r.reportName(), ok ? 1 : 0);
                break;
            } else {
                log.debug("[parse] {} {} rcpNo={} (no match)", corpCode, ym, r.rcpNo());
            }
        }
        return saved;
    }

    private boolean upsertKpiDonation(Organization org, int year, int month, BigDecimal value, String source) {
        var metric = KpiMonthly.Metric.DONATION_AMOUNT_KRW;
        var existing = kpiRepo.findByOrganizationAndMetricAndYearAndMonth(org, metric, year, month);
        if (existing.isPresent()) {
            var k = existing.get();
            if (k.getDecimalValue() == null || k.getDecimalValue().compareTo(value) != 0) {
                k.setDecimalValue(value);
                k.setSource(source);
                kpiRepo.save(k);
            }
            return false;
        } else {
            var k = new KpiMonthly();
            k.setOrganization(org);
            k.setMetric(metric);
            k.setYear(year);
            k.setMonth(month);
            k.setDecimalValue(value);
            k.setSource(source);
            kpiRepo.save(k);
            return true;
        }
    }
}

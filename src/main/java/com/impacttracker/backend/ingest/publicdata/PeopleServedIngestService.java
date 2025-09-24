package com.impacttracker.backend.ingest.publicdata;

import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 공개데이터/내부 지표 등에서 "사람에게 미친 긍정적 영향"을 월별 KPI로 적재하는 서비스.
 * - PEOPLE_SERVED_COUNT (long)
 * - VOLUNTEER_HOURS (long)
 * - COMMUNITY_DONATION_MATCH_KRW (decimal)
 *
 * 주의: KpiMonthlyRepository에는 upsertAll 같은 메서드가 없습니다.
 * 여기서 find → update 또는 insert(=upsert)를 직접 수행합니다.
 */
@Service
public class PeopleServedIngestService {

    private static final Logger log = LoggerFactory.getLogger(PeopleServedIngestService.class);

    private final KpiMonthlyRepository kpiRepo;

    public PeopleServedIngestService(KpiMonthlyRepository kpiRepo) {
        this.kpiRepo = kpiRepo;
    }

    /**
     * 한 조직(org)에 대해 특정 월(ym)의 3개 지표를 upsert.
     *
     * @return 저장(신규 insert)된 레코드 개수
     */
    public int ingest(Organization org,
                      YearMonth ym,
                      Long peopleServed,
                      Long volunteerHours,
                      BigDecimal communityDonationMatchKrw,
                      String source,
                      String note) {

        int saved = 0;
        if (peopleServed != null) {
            saved += upsertLong(org, KpiMonthly.Metric.PEOPLE_SERVED_COUNT, ym, peopleServed, source, note) ? 1 : 0;
        }
        if (volunteerHours != null) {
            saved += upsertLong(org, KpiMonthly.Metric.VOLUNTEER_HOURS, ym, volunteerHours, source, note) ? 1 : 0;
        }
        if (communityDonationMatchKrw != null) {
            saved += upsertDecimal(org, KpiMonthly.Metric.COMMUNITY_DONATION_MATCH_KRW, ym, communityDonationMatchKrw, source, note) ? 1 : 0;
        }
        log.info("[ingest][PUBLIC] orgId={} ym={} inserted={}", org.getId(), ym, saved);
        return saved;
    }

    private boolean upsertLong(Organization org,
                               KpiMonthly.Metric metric,
                               YearMonth ym,
                               Long value,
                               String source,
                               String note) {

        Optional<KpiMonthly> existing = kpiRepo.findByOrganizationAndMetricAndYearAndMonth(
                org, metric, ym.getYear(), ym.getMonthValue());

        if (existing.isPresent()) {
            var k = existing.get();
            if (k.getLongValue() == null || !k.getLongValue().equals(value)
                    || different(k.getSource(), source) || different(k.getNote(), note)) {
                k.setLongValue(value);
                k.setSource(source);
                k.setNote(note);
                kpiRepo.save(k);
            }
            return false; // 신규 insert 아님
        } else {
            var k = new KpiMonthly();
            k.setOrganization(org);
            k.setMetric(metric);
            k.setYear(ym.getYear());
            k.setMonth(ym.getMonthValue());
            k.setLongValue(value);
            k.setSource(source);
            k.setNote(note);
            kpiRepo.save(k);
            return true; // 신규 insert
        }
    }

    private boolean upsertDecimal(Organization org,
                                  KpiMonthly.Metric metric,
                                  YearMonth ym,
                                  BigDecimal value,
                                  String source,
                                  String note) {

        Optional<KpiMonthly> existing = kpiRepo.findByOrganizationAndMetricAndYearAndMonth(
                org, metric, ym.getYear(), ym.getMonthValue());

        if (existing.isPresent()) {
            var k = existing.get();
            if (k.getDecimalValue() == null || k.getDecimalValue().compareTo(value) != 0
                    || different(k.getSource(), source) || different(k.getNote(), note)) {
                k.setDecimalValue(value);
                k.setSource(source);
                k.setNote(note);
                kpiRepo.save(k);
            }
            return false;
        } else {
            var k = new KpiMonthly();
            k.setOrganization(org);
            k.setMetric(metric);
            k.setYear(ym.getYear());
            k.setMonth(ym.getMonthValue());
            k.setDecimalValue(value);
            k.setSource(source);
            k.setNote(note);
            kpiRepo.save(k);
            return true;
        }
    }

    private static boolean different(String a, String b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return !a.equals(b);
    }
}

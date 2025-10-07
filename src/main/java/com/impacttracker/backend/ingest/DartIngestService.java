package com.impacttracker.backend.ingest;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartApiClient;
import com.impacttracker.backend.ingest.dart.DartQuotaExceededException;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

@Service
public class DartIngestService {
    private static final Logger log = LoggerFactory.getLogger(DartIngestService.class);

    private final DartApiClient dart;
    private final KpiMonthlyRepository kpiRepo;

    public DartIngestService(DartApiClient dart, KpiMonthlyRepository kpiRepo) {
        this.dart = dart;
        this.kpiRepo = kpiRepo;
    }

    /**
     * 연도별 기부금 수집 (XBRL API 사용)
     */
    public int ingestDonationForYear(String corpCode, Organization org, int year) {
        try {
            // ★★★ XBRL API 호출 (판관비 상세 포함) ★★★
            BigDecimal amount = dart.fetchDonationFromXbrlAll(corpCode, year);

            if (amount == null || amount.signum() <= 0) {
                log.debug("[DART] XBRL에서 기부금 없음 corp={} year={}", corpCode, year);
                return 0;
            }

            // 10만원 미만 필터 (파싱 오류 방지)
            if (amount.compareTo(new BigDecimal("100000")) < 0) {
                log.warn("[DART] ⚠️ 금액이 너무 작음 (무시) corp={} year={} amount={}",
                        corpCode, year, amount);
                return 0;
            }

            // 1000조 초과 필터 (오탐 방지)
            if (amount.compareTo(new BigDecimal("1000000000000000")) > 0) {
                log.warn("[DART] ⚠️ 금액이 너무 큼 (무시) corp={} year={} amount={}",
                        corpCode, year, amount);
                return 0;
            }

            log.info("[DART] ✅ corp={} year={} amount={}", corpCode, year, amount);

            // 3월에 저장 (사업보고서 제출월)
            boolean ok = upsertKpiDonation(org, year, 3, amount, "DART:XBRL:" + year);
            return ok ? 1 : 0;

        } catch (DartQuotaExceededException q) {
            throw q; // 쿼터 초과는 상위로 전파
        } catch (Exception e) {
            log.warn("[DART] 수집 실패 corp={} year={} cause={}", corpCode, year, e.toString());
            return 0;
        }
    }

    /**
     * KPI 기부금 업서트
     */
    private boolean upsertKpiDonation(Organization org, int year, int month,
                                      BigDecimal value, String source) {
        String periodYm = String.format("%04d%02d", year, month);
        Long orgId = org.getId();
        KpiMetric metric = KpiMetric.DONATION_AMOUNT_KRW;

        Optional<KpiMonthly> existing = kpiRepo.findByOrgIdAndMetricAndPeriodYm(
                orgId, metric, periodYm
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
                log.debug("[DART] 🔄 updated id={} orgId={} ym={} value={}",
                        k.getId(), orgId, periodYm, value);
            }
            return false; // 이미 존재
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
            log.debug("[DART] ✅ inserted orgId={} ym={} value={}", orgId, periodYm, value);
            return true; // 새로 삽입
        }
    }
}
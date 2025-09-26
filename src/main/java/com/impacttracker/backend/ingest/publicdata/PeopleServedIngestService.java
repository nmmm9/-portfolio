package com.impacttracker.backend.ingest.publicdata;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.service.KpiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * 공공데이터 등에서 수집된 "수혜 인원(PEOPLE_SERVED_COUNT)" 월별 데이터를
 * kpi_monthly 로 적재하는 서비스.
 *
 * 기존 코드에서 KpiMonthly.Metric 을 사용하던 부분을 KpiMetric 으로 전환했습니다.
 */
@Service
public class PeopleServedIngestService {

    private static final Logger log = LoggerFactory.getLogger(PeopleServedIngestService.class);
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final KpiService kpiService;

    public PeopleServedIngestService(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    /**
     * 월별 맵(YYYY-MM -> 값)을 한 번에 적재.
     * metric 파라미터를 받는 형태를 유지(이전 호출부 호환)하되, 기본값은 PEOPLE_SERVED_COUNT.
     */
    public int ingestMonthlyMap(Long orgId,
                                Long projectId,
                                Map<String, BigDecimal> periodYmToValue,
                                KpiMetric metric,
                                String source,
                                boolean approved) {

        Objects.requireNonNull(orgId, "orgId");
        if (metric == null) metric = KpiMetric.PEOPLE_SERVED_COUNT;

        int ok = 0;
        if (periodYmToValue == null || periodYmToValue.isEmpty()) {
            log.info("[people-served] no data to ingest (orgId={})", orgId);
            return 0;
        }

        for (Map.Entry<String, BigDecimal> e : periodYmToValue.entrySet()) {
            String ym = normalizeYm(e.getKey());
            BigDecimal v = safeNonNull(e.getValue());
            try {
                upsert(orgId, projectId, ym, metric, v, source, approved);
                ok++;
            } catch (Exception ex) {
                log.warn("[people-served] upsert failed orgId={} ym={} v={} cause={}",
                        orgId, ym, v, ex.toString());
            }
        }
        log.info("[people-served] ingested {} rows (orgId={})", ok, orgId);
        return ok;
    }

    /**
     * 개별 월 레코드 적재 (호출부 호환용 시그니처).
     * 이전에는 KpiMonthly.Metric metric 이었으나 지금은 KpiMetric 으로 변경.
     */
    public void upsert(Long orgId,
                       Long projectId,
                       String periodYm,
                       KpiMetric metric,
                       BigDecimal value,
                       String source,
                       boolean approved) {

        if (metric == null) metric = KpiMetric.PEOPLE_SERVED_COUNT;
        if (value == null) value = BigDecimal.ZERO;

        String ym = normalizeYm(periodYm);
        kpiService.upsertMonthly(orgId, projectId, ym, metric, value, source == null ? "PUBLIC_DATA" : source, approved);
    }

    /**
     * 편의 오버로드: metric/source/approved 생략 시 기본값 사용.
     */
    public void upsert(Long orgId,
                       Long projectId,
                       String periodYm,
                       BigDecimal value) {
        upsert(orgId, projectId, periodYm, KpiMetric.PEOPLE_SERVED_COUNT, value, "PUBLIC_DATA", true);
    }

    // ---------- helpers ----------

    private String normalizeYm(String input) {
        if (input == null || input.isBlank()) {
            return LocalDate.now().format(YM);
        }
        String s = input.trim();
        // 허용: "YYYY-MM" 또는 "YYYYMM"
        if (s.matches("\\d{4}-\\d{2}")) {
            return s;
        }
        if (s.matches("\\d{6}")) {
            return s.substring(0, 4) + "-" + s.substring(4, 6);
        }
        // 기타 포맷은 LocalDate 파싱 시도(첫날로 가정)
        try {
            LocalDate d = LocalDate.parse(s);
            return d.format(YM);
        } catch (Exception ignore) {
            log.warn("[people-served] invalid periodYm '{}', fallback to current month", s);
            return LocalDate.now().format(YM);
        }
    }

    private BigDecimal safeNonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

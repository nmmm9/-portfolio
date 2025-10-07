package com.impacttracker.backend.service;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import com.impacttracker.backend.repo.KpiSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class KpiService {
    private static final Logger log = LoggerFactory.getLogger(KpiService.class);

    private final KpiMonthlyRepository monthlyRepo;
    private final KpiSubmissionRepository submissionRepo;

    public KpiService(KpiMonthlyRepository m, KpiSubmissionRepository s) {
        this.monthlyRepo = m;
        this.submissionRepo = s;
    }

    public Map<KpiMetric, BigDecimal> rollingSum(Long orgId, int months) {
        String toYm = ym(LocalDate.now());
        String fromYm = ym(LocalDate.now().minusMonths(months - 1));
        Map<KpiMetric, BigDecimal> out = new EnumMap<>(KpiMetric.class);
        for (Object[] row : monthlyRepo.sumByMetric(orgId, fromYm, toYm)) {
            out.put((KpiMetric) row[0], (BigDecimal) row[1]);
        }
        return out;
    }

    public double approvalRate(Long orgId, int months) {
        String toYm = ym(LocalDate.now());
        String fromYm = ym(LocalDate.now().minusMonths(months - 1));
        long total = submissionRepo.countInWindow(orgId, fromYm, toYm);
        if (total == 0) return 0.0;
        long ok = submissionRepo.countApprovedInWindow(orgId, fromYm, toYm);
        return Math.round((ok * 10000.0 / total)) / 100.0;
    }

    public List<KpiMonthly> series(Long orgId, KpiMetric metric) {
        return monthlyRepo.findByOrgIdAndMetricAndApprovedOrderByPeriodYmAsc(orgId, metric, true);
    }

    /** DB와 맞추기 위해 yyyyMM로 통일 */
    private String ym(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 업서트(덮어쓰기):
     * 1순위: (orgId, metric, periodYm, source)
     * 2순위: source가 비거나 없을 때 (orgId, projectId, periodYm, metric)
     */
    @Transactional
    public void upsertMonthly(Long orgId, Long projectId, String periodYm, KpiMetric metric,
                              BigDecimal value, String source, boolean approved) {
        if (value == null) value = BigDecimal.ZERO;

        // ★ periodYm 정규화 (yyyyMM 또는 yyyy-MM → yyyyMM로 통일)
        periodYm = normalizePeriodYm(periodYm);

        KpiMonthly target = null;

        // 1) source가 있으면 source까지 포함해서 찾기
        if (source != null && !source.isBlank()) {
            target = monthlyRepo
                    .findByOrgIdAndMetricAndPeriodYmAndSource(orgId, metric, periodYm, source)
                    .orElse(null);
        }

        // 2) 못 찾고, projectId가 있으면 projectId 키로 보조 매칭
        if (target == null && projectId != null) {
            target = monthlyRepo
                    .findFirstByOrgIdAndProjectIdAndPeriodYmAndMetric(orgId, projectId, periodYm, metric)
                    .orElse(null);
        }

        // 3) 여전히 못 찾으면 (orgId, metric, periodYm)만으로 찾기
        if (target == null) {
            target = monthlyRepo
                    .findByOrgIdAndMetricAndPeriodYm(orgId, metric, periodYm)
                    .orElse(null);
        }

        if (target == null) {
            // INSERT
            KpiMonthly k = new KpiMonthly();
            k.setOrgId(orgId);
            k.setProjectId(projectId);
            k.setPeriodYm(periodYm);
            k.setMetric(metric);
            k.setValue(value);
            k.setSource(source);
            k.setApproved(approved);
            monthlyRepo.save(k);
            log.info("[KPI] ✅ inserted orgId={} metric={} ym={} value={} source={}",
                    orgId, metric, periodYm, value, source);
        } else {
            // UPDATE (변경된 경우만)
            boolean changed = false;

            if (!value.equals(target.getValue())) {
                target.setValue(value);
                changed = true;
            }

            if (projectId != null && !projectId.equals(target.getProjectId())) {
                target.setProjectId(projectId);
                changed = true;
            }

            if (source != null && !source.equals(target.getSource())) {
                target.setSource(source);
                changed = true;
            }

            if (approved != target.isApproved()) {
                target.setApproved(approved);
                changed = true;
            }

            if (changed) {
                monthlyRepo.save(target);
                log.info("[KPI] 🔄 updated id={} orgId={} metric={} ym={} value={} source={}",
                        target.getId(), orgId, metric, periodYm, value, source);
            } else {
                log.debug("[KPI] ⏭️ skipped (no change) orgId={} metric={} ym={}",
                        orgId, metric, periodYm);
            }
        }
    }

    /**
     * periodYm 정규화: yyyyMM 형식으로 통일
     */
    private String normalizePeriodYm(String ym) {
        if (ym == null || ym.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        }

        String s = ym.trim();

        // yyyy-MM → yyyyMM
        if (s.matches("\\d{4}-\\d{2}")) {
            return s.replace("-", "");
        }

        // yyyyMM → 그대로
        if (s.matches("\\d{6}")) {
            return s;
        }

        // 기타 형식 → 현재 월로 폴백
        log.warn("[KPI] invalid periodYm '{}', fallback to current month", s);
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
}
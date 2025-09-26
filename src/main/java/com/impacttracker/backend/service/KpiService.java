package com.impacttracker.backend.service;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import com.impacttracker.backend.repo.KpiSubmissionRepository;
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
        if (periodYm != null && periodYm.contains("-")) {
            periodYm = periodYm.replace("-", ""); // yyyyMM 강제
        }

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
        } else {
            // UPDATE (덮어쓰기)
            target.setProjectId(projectId);
            target.setValue(value);
            target.setApproved(approved);
            if (source != null && !source.isBlank()) {
                target.setSource(source); // 기존 source가 빈 값이면 갱신
            }
            monthlyRepo.save(target);
        }
    }
}

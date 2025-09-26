package com.impacttracker.backend.dev;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import com.impacttracker.backend.repo.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;

/**
 * dev 환경용 더미 KPI 시더 – 기본값은 disabled.
 * application.yml에 impacttracker.dev-seed.enabled=true 로 명시해야만 실행됩니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "impacttracker.dev-seed.enabled", havingValue = "true")
public class DevKpiSeeder implements ApplicationRunner {

    private final OrganizationRepository orgRepo;
    private final KpiMonthlyRepository kpiRepo;

    @Override
    public void run(ApplicationArguments args) {
        // 기존 로직 그대로
        int maxOrgs = getInt("seed.maxOrgs", 5);
        int months  = getInt("seed.months", 3);

        List<Organization> orgs = orgRepo.findAllDartEnabled();
        if (orgs == null || orgs.isEmpty()) {
            log.info("[seed] no organizations to seed");
            return;
        }

        orgs = orgs.stream()
                .filter(o -> o.getId() != null)
                .limit(maxOrgs)
                .toList();

        YearMonth now = YearMonth.now();
        Random rnd = new Random(7);

        int created = 0;
        for (Organization org : orgs) {
            Long orgId = org.getId();
            for (int i = 0; i < months; i++) {
                YearMonth ym = now.minusMonths(i);
                String periodYm = toYm(ym);

                created += upsertIfAbsent(orgId, KpiMetric.DONATION_AMOUNT_KRW, periodYm,
                        BigDecimal.valueOf(10_000_000L + rnd.nextInt(5_000_000)), "SEED:DEV");

                created += upsertIfAbsent(orgId, KpiMetric.PEOPLE_SERVED_COUNT, periodYm,
                        BigDecimal.valueOf(500 + rnd.nextInt(500)), "SEED:DEV");

                created += upsertIfAbsent(orgId, KpiMetric.VOLUNTEER_HOURS, periodYm,
                        BigDecimal.valueOf(100 + rnd.nextInt(200)), "SEED:DEV");

                try {
                    created += upsertIfAbsent(orgId, KpiMetric.valueOf("CO2E_REDUCED_TON"), periodYm,
                            BigDecimal.valueOf(50 + rnd.nextInt(50)), "SEED:DEV");
                } catch (IllegalArgumentException ignored) {}
            }
        }

        log.info("[seed] done: created {}", created);
    }

    private int upsertIfAbsent(Long orgId, KpiMetric metric, String periodYm, BigDecimal value, String source) {
        return kpiRepo.findByOrgIdAndMetricAndPeriodYm(orgId, metric, periodYm)
                .map(existing -> 0)
                .orElseGet(() -> {
                    KpiMonthly k = new KpiMonthly();
                    k.setOrgId(orgId);
                    k.setProjectId(null);
                    k.setPeriodYm(periodYm);
                    k.setMetric(metric);
                    k.setValue(value);
                    k.setSource(source);
                    k.setApproved(true);
                    kpiRepo.save(k);
                    return 1;
                });
    }

    private static String toYm(YearMonth ym) {
        return String.format("%04d%02d", ym.getYear(), ym.getMonthValue());
    }

    private static int getInt(String key, int def) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }
}

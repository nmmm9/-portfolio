package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface KpiMonthlyRepository extends JpaRepository<KpiMonthly, Long> {

    Optional<KpiMonthly> findByOrganizationAndMetricAndYearAndMonth(
            Organization organization,
            KpiMonthly.Metric metric,
            int year,
            int month
    );

    @Query("""
           select k from KpiMonthly k
           where k.organization.id = :orgId
             and k.metric = :metric
             and k.year = :year
             and k.month = :month
           """)
    Optional<KpiMonthly> findByOrgIdAndMetricAndYearAndMonth(
            Long orgId,
            KpiMonthly.Metric metric,
            int year,
            int month
    );
}

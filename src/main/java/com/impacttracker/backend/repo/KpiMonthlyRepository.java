package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.KpiMetric;
import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiMonthlyRepository extends JpaRepository<KpiMonthly, Long> {

    @Query("select k.metric as metric, sum(k.value) as total "
            + "from KpiMonthly k where k.orgId=:orgId and k.approved=true "
            + "and k.periodYm between :fromYm and :toYm group by k.metric")
    List<Object[]> sumByMetric(Long orgId, String fromYm, String toYm);
    // KpiMonthlyRepository 에 추가(인터페이스 메서드 시그니처만 적으면 Spring Data가 구현)
    Optional<KpiMonthly> findFirstByOrgIdAndProjectIdAndPeriodYmAndMetric(Long orgId, Long projectId, String periodYm, KpiMetric metric);
    List<KpiMonthly> findByOrgIdAndMetricAndApprovedOrderByPeriodYmAsc(Long orgId, KpiMetric metric, boolean approved);
    Optional<KpiMonthly> findByOrgIdAndMetricAndPeriodYm(Long orgId, KpiMetric metric, String periodYm);
    Optional<KpiMonthly> findByOrgIdAndMetricAndPeriodYmAndSource(Long orgId, KpiMetric metric, String periodYm, String source);
}

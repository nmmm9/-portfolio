package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.KpiSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KpiSubmissionRepository extends JpaRepository<KpiSubmission, Long> {

    @Query("select count(s) from KpiSubmission s where s.orgId=:orgId and s.periodYm between :fromYm and :toYm")
    long countInWindow(Long orgId, String fromYm, String toYm);

    @Query("select count(s) from KpiSubmission s where s.orgId=:orgId and s.status='APPROVED' "
            + "and s.periodYm between :fromYm and :toYm")
    long countApprovedInWindow(Long orgId, String fromYm, String toYm);

    List<KpiSubmission> findTop20ByOrgIdAndStatusOrderByReviewedAtDesc(Long orgId, KpiSubmission.Status status);
}
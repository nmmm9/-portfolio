package com.impacttracker.backend.api;

import com.impacttracker.backend.domain.KpiMonthly;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.domain.Project;
import com.impacttracker.backend.domain.VerificationLog;
import com.impacttracker.backend.repo.VerificationLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/verify")
public class VerificationController {

    private final VerificationLogRepository repo;
    private final EntityManager em;

    public VerificationController(VerificationLogRepository repo, EntityManager em) {
        this.repo = repo;
        this.em = em;
    }

    public record VerifyReq(Long orgId, Long projectId, Long kpiMonthlyId, String evidenceUrl, String auditor){}

    @PostMapping("/submit")
    @Transactional
    public void submit(@RequestBody VerifyReq req){
        VerificationLog v = new VerificationLog();

        // JPA 프록시 레퍼런스 사용 (DB hit 없이 FK만 세팅)
        Organization orgRef = em.getReference(Organization.class, req.orgId());
        v.setOrganization(orgRef);

        if (req.projectId()!=null) {
            Project projRef = em.getReference(Project.class, req.projectId());
            v.setProject(projRef);
        }
        if (req.kpiMonthlyId()!=null) {
            KpiMonthly kpiRef = em.getReference(KpiMonthly.class, req.kpiMonthlyId());
            v.setKpiMonthly(kpiRef);
        }

        v.setStatus(VerificationLog.Status.SUBMITTED);
        v.setEvidenceUrl(req.evidenceUrl());
        v.setAuditor(req.auditor());

        repo.save(v);
    }

    @PostMapping("/approve/{id}")
    @Transactional
    public void approve(@PathVariable Long id){
        repo.findById(id).ifPresent(v -> {
            v.setStatus(VerificationLog.Status.APPROVED);
            repo.save(v);
        });
    }

    @PostMapping("/reject/{id}")
    @Transactional
    public void reject(@PathVariable Long id){
        repo.findById(id).ifPresent(v -> {
            v.setStatus(VerificationLog.Status.REJECTED);
            repo.save(v);
        });
    }
}

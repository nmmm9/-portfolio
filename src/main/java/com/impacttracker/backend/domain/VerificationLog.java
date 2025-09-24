package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class VerificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="organization_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name="project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name="kpi_monthly_id")
    private KpiMonthly kpiMonthly;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Status status; // SUBMITTED, APPROVED, REJECTED

    @Lob
    private String evidenceUrl;

    private String auditor;

    @Column(nullable=false)
    private Instant createdAt = Instant.now();

    public enum Status { SUBMITTED, APPROVED, REJECTED }

    // --- getters/setters ---
    public Long getId() { return id; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public KpiMonthly getKpiMonthly() { return kpiMonthly; }
    public void setKpiMonthly(KpiMonthly kpiMonthly) { this.kpiMonthly = kpiMonthly; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }

    public String getAuditor() { return auditor; }
    public void setAuditor(String auditor) { this.auditor = auditor; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "kpi_monthly",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_kpi_monthly",
                columnNames = {"orgId","projectId","periodYm","metric"}
        )
)
public class KpiMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** organization.id */
    @Column(nullable = false)
    private Long orgId;

    /** nullable: 프로젝트 단위 집계용 */
    private Long projectId;

    /** YYYY-MM */
    @Column(length = 7, nullable = false)
    private String periodYm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private KpiMetric metric;

    @Column(precision = 20, scale = 4, nullable = false)
    private BigDecimal value;

    /** DART/ESG/PUBLIC_DATA/MANUAL 등 */
    @Column(length = 50)
    private String source;

    @Column(nullable = false)
    private boolean approved = true;

    private Instant createdAt = Instant.now();

    // ---------- getters / setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getPeriodYm() { return periodYm; }
    public void setPeriodYm(String periodYm) { this.periodYm = periodYm; }

    public KpiMetric getMetric() { return metric; }
    public void setMetric(KpiMetric metric) { this.metric = metric; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

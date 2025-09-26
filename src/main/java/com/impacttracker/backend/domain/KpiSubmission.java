package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "kpi_submission")
public class KpiSubmission {
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private Long projectId;
    @Enumerated(EnumType.STRING)
    private KpiMetric metric;
    private String periodYm;
    private BigDecimal valueSubmitted;
    private String evidenceUrl;
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    private String submittedBy;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reason;
    private Instant createdAt;

    // getters/setters
}

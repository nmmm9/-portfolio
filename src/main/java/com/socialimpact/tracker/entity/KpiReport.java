package com.socialimpact.tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "kpi_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kpi_id")
    private Kpi kpi;

    @Column(precision = 15, scale = 2)
    private BigDecimal value;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    private List<Evidence> evidences;

    public enum ReportStatus {
        PENDING, APPROVED, REJECTED
    }
}
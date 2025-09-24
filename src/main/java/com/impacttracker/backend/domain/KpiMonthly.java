package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kpi_monthly",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kpi_monthly_org_metric_ym",
                        columnNames = {"organization_id", "metric", "year", "month"})
        },
        indexes = {
                @Index(name = "idx_kpi_monthly_org", columnList = "organization_id"),
                @Index(name = "idx_kpi_monthly_metric", columnList = "metric"),
                @Index(name = "idx_kpi_monthly_ym", columnList = "year, month")
        }
)
public class KpiMonthly {

    public enum Metric {
        /** DART 등에서 적재하는 기부금(원화) */
        DONATION_AMOUNT_KRW,

        /** 긍정적 영향: 도움을 받은 사람 수(명) */
        PEOPLE_SERVED_COUNT,

        /** 자원봉사 시간(시간) */
        VOLUNTEER_HOURS,

        /** 커뮤니티 매칭 기부(원화) */
        COMMUNITY_DONATION_MATCH_KRW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 조직(회사) FK */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Metric metric;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    /** 정수형 지표 값(명/시간 등) */
    @Column(name = "long_value")
    private Long longValue;

    /** 금액/비율 등 소수 포함 지표 */
    @Column(name = "decimal_value", precision = 22, scale = 4)
    private BigDecimal decimalValue;

    /** 수집/출처 태그 (예: "DART:2024-반기보고서 rcpNo=...") */
    @Column(name = "source", length = 255)
    private String source;

    /** 비고(파싱노트 등) */
    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ====== getters / setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public Metric getMetric() { return metric; }
    public void setMetric(Metric metric) { this.metric = metric; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public Long getLongValue() { return longValue; }
    public void setLongValue(Long longValue) { this.longValue = longValue; }

    public BigDecimal getDecimalValue() { return decimalValue; }
    public void setDecimalValue(BigDecimal decimalValue) { this.decimalValue = decimalValue; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

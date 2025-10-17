package com.socialimpact.tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "donations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "year", "quarter"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "quarter")
    private Integer quarter; // 1, 2, 3, 4 (분기)

    @Column(name = "donation_amount", precision = 20, scale = 2, nullable = false)
    private BigDecimal donationAmount;

    @Column(length = 100)
    private String currency; // KRW, USD 등

    @Column(name = "report_type", length = 50)
    private String reportType; // "1분기보고서", "반기보고서", "사업보고서" 등

    @Column(name = "fiscal_month")
    private Integer fiscalMonth; // 결산월 (12월 결산 = 12)

    @Column(name = "data_source", length = 50)
    private String dataSource; // "CSV_2023", "DART_AUTO" 등

    @Column(name = "verification_status", length = 20)
    private String verificationStatus; // "검증완료", "수동입력", "자동수집"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getOrganizationId() {
        return organization != null ? organization.getId() : null;
    }
}
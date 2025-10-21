package com.socialimpact.tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "employments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "year"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employment {

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

    // 총 인원
    @Column(name = "total_employees")
    private Integer totalEmployees;

    // 성별 인원
    @Column(name = "male_employees")
    private Integer maleEmployees;

    @Column(name = "female_employees")
    private Integer femaleEmployees;

    // 고용 형태
    @Column(name = "regular_employees")
    private Integer regularEmployees; // 정규직

    @Column(name = "contract_employees")
    private Integer contractEmployees; // 계약직

    @Column(name = "temporary_employees")
    private Integer temporaryEmployees; // 임시직

    // 평균 근속연수
    @Column(name = "average_service_years")
    private Double averageServiceYears;

    // 신규 채용
    @Column(name = "new_hires")
    private Integer newHires;

    // 퇴직자
    @Column(name = "resigned")
    private Integer resigned;

    // 이직률
    @Column(name = "turnover_rate")
    private Double turnoverRate; // 퇴직자 / 평균 직원 수 * 100

    @Column(name = "data_source", length = 50)
    private String dataSource; // "DART_API", "MANUAL" 등

    @Column(name = "verification_status", length = 20)
    private String verificationStatus; // "검증완료", "자동수집"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // 이직률 자동 계산
        if (resigned != null && totalEmployees != null && totalEmployees > 0) {
            turnoverRate = (resigned.doubleValue() / totalEmployees) * 100;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // 이직률 자동 계산
        if (resigned != null && totalEmployees != null && totalEmployees > 0) {
            turnoverRate = (resigned.doubleValue() / totalEmployees) * 100;
        }
    }

    public Long getOrganizationId() {
        return organization != null ? organization.getId() : null;
    }

    // 여성 비율 계산
    public Double getFemaleRatio() {
        if (totalEmployees == null || totalEmployees == 0 || femaleEmployees == null) {
            return 0.0;
        }
        return (femaleEmployees.doubleValue() / totalEmployees) * 100;
    }

    // 정규직 비율 계산
    public Double getRegularRatio() {
        if (totalEmployees == null || totalEmployees == 0 || regularEmployees == null) {
            return 0.0;
        }
        return (regularEmployees.doubleValue() / totalEmployees) * 100;
    }
}
package com.socialimpact.tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "emissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Emission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore  // ⭐ JSON 직렬화 시 제외 (LazyInitializationException 방지)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "organization_name", length = 200)
    private String organizationName;  // 회사명 중복 저장 (조회 성능 향상)

    @Column(name = "gir_company_name", length = 200)
    private String girCompanyName;  // GIR 엑셀의 원본 법인명

    @Column(nullable = false)
    private Integer year;

    @Column(name = "total_emissions", precision = 15, scale = 2)
    private BigDecimal totalEmissions;

    @Column(name = "scope1", precision = 15, scale = 2)
    private BigDecimal scope1;

    @Column(name = "scope2", precision = 15, scale = 2)
    private BigDecimal scope2;

    @Column(name = "scope3", precision = 15, scale = 2)
    private BigDecimal scope3;

    @Column(name = "energy_usage", precision = 15, scale = 2)
    private BigDecimal energyUsage;

    @Column(length = 200)
    private String industry;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "verification_status", length = 20)
    private String verificationStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
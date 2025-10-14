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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Column(name = "gir_company_name", length = 200)
    private String girCompanyName;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "total_emissions", precision = 15, scale = 2)
    private BigDecimal totalEmissions;

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

    // organizationId getter 추가 (JSON 직렬화용)
    public Long getOrganizationId() {
        return organization != null ? organization.getId() : null;
    }
}
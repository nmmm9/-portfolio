package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "organization",
        indexes = {
                @Index(name="ix_org_corp_code", columnList="corp_code"),
                @Index(name="ix_org_stock_code", columnList="stock_code")
        },
        uniqueConstraints = {
                @UniqueConstraint(name="uk_org_corp_code", columnNames = {"corp_code"})
        })
public class Organization {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="name", nullable=false)
    private String name;

    @Column(name="corp_code", nullable=false, length=8)
    private String corpCode;

    @Column(name="stock_code", length=6)
    private String stockCode;

    @Column(name="biz_reg_no") private String bizRegNo;
    @Column(name="country") private String country;
    @Column(name="region") private String region;

    @CreationTimestamp
    @Column(name="created_at", updatable=false)
    private LocalDateTime createdAt;

    // --- getters/setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCorpCode() { return corpCode; }
    public void setCorpCode(String corpCode) { this.corpCode = corpCode; }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public String getBizRegNo() { return bizRegNo; }
    public void setBizRegNo(String bizRegNo) { this.bizRegNo = bizRegNo; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    // equals/hashCode: corpCode 기준 (동명이인 허용)
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Organization that)) return false;
        return corpCode != null && corpCode.equals(that.corpCode);
    }
    @Override public int hashCode() { return Objects.hash(corpCode); }
}

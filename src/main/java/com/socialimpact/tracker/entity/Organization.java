package com.socialimpact.tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "organizations")
@Data
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String type;

    // DART API corp_code
    @Column(name = "corp_code", length = 8)
    private String corpCode;

    // Stock code
    @Column(name = "stock_code", length = 6)
    private String stockCode;

    // Industry field - CRITICAL: Must match DB column
    @Column(length = 50)
    private String industry;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Updated at field - CRITICAL: Must match DB column
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Project> projects;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
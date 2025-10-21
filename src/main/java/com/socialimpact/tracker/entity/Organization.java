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

    // ⭐ DART API 용 corp_code 추가
    @Column(name = "corp_code", length = 8)
    private String corpCode;

    // ⭐ 종목코드 추가
    @Column(name = "stock_code", length = 6)
    private String stockCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Project> projects;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
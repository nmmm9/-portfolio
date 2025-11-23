package com.socialimpact.tracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "positive_news")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositiveNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "organization_name", length = 200, nullable = false)
    private String organizationName;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 1000, unique = true)
    private String url;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    @Column(length = 50)
    private String source = "NAVER";

    @Column(length = 50)
    private String category;

    @Column(name = "matched_keywords", columnDefinition = "TEXT")
    private String matchedKeywords;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Helper methods
    public Long getOrganizationId() {
        return organization != null ? organization.getId() : null;
    }

    public Integer getYear() {
        return publishedDate != null ? publishedDate.getYear() : null;
    }
}
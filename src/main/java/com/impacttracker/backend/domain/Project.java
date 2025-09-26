package com.impacttracker.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "project")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private String name;
    private String category;
    private String region;
    @Column(columnDefinition = "text")
    private String description;
    private Instant createdAt;

    // getters/setters
}

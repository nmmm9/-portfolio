package com.impacttracker.backend.domain;

import jakarta.persistence.*;

@Entity
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false) @JoinColumn(name="organization_id")
    private Organization organization;

    @Column(nullable=false) private String name;
    @Enumerated(EnumType.STRING)
    private Category category = Category.OTHER;
    private String region;

    public enum Category { ENVIRONMENT, EDUCATION, CHILDREN, OTHER }
    // getters/setters
}

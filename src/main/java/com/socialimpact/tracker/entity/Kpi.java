package com.socialimpact.tracker.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "kpis")
@Data
public class Kpi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String unit;

    @Column(length = 50)
    private String category;
}

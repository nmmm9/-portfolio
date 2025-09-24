package com.impacttracker.backend.domain;

import jakarta.persistence.*;

@Entity
public class AccountAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false) private String alias;       // '기부금', '사회공헌비용'
    @Column(nullable=false) private String normalized;  // 'DONATION'
    // getters/setters
}

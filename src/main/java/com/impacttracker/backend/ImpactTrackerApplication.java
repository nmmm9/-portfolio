package com.impacttracker.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ImpactTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImpactTrackerApplication.class, args);
    }
}

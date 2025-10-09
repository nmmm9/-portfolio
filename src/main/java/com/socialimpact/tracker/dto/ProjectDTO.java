package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {
    private Long id;
    private String name;
    private String category;
    private String organizationName;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<KpiSummary> kpiSummaries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiSummary {
        private String kpiName;
        private String value;
        private String unit;
    }
}

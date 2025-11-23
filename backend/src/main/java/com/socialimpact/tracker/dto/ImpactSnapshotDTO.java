package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImpactSnapshotDTO {
    private List<ProjectImpact> projectImpacts;
    private List<CategoryImpact> categoryImpacts;
    private List<RegionImpact> regionImpacts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectImpact {
        private String projectName;
        private BigDecimal co2Reduced;
        private BigDecimal volunteerHours;
        private BigDecimal donation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryImpact {
        private String category;
        private BigDecimal value;
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionImpact {
        private String region;
        private BigDecimal value;
        private Integer count;
    }
}

package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO {
    private Long id;
    private String projectName;
    private String kpiName;
    private String value;
    private String status;
    private String activityType;
    private LocalDateTime timestamp;
    private String approvedBy;
}

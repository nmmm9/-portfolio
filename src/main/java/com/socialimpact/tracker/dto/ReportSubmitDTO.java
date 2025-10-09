package com.socialimpact.tracker.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReportSubmitDTO {
    private Long projectId;
    private Long kpiId;
    private BigDecimal value;
    private LocalDate reportDate;
    private String[] evidenceUrls;
}


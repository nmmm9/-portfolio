package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    private BigDecimal totalCo2Reduced;
    private BigDecimal totalVolunteerHours;
    private BigDecimal totalDonation;
    private Long totalPeopleServed;
}
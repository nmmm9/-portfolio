package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickKpiDTO {
    private BigDecimal co2Reduced;
    private BigDecimal volunteerHours;
    private BigDecimal donation;
    private BigDecimal reportApprovalRate;
    private List<MonthlyKpiData> monthlyData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyKpiData {
        private String month;
        private BigDecimal co2Reduced;
        private BigDecimal volunteerHours;
        private BigDecimal donation;
    }
}
package com.socialimpact.tracker.dto;

import lombok.Data;

@Data
public class ReportApprovalDTO {
    private Long reportId;
    private String status; // APPROVED or REJECTED
    private String approvedBy;
    private String comment;
}
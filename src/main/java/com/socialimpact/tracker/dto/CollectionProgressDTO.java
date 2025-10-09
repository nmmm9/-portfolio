package com.socialimpact.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionProgressDTO {
    private boolean isCollecting;           // 수집 중 여부
    private int totalCompanies;             // 전체 기업 수
    private int processedCompanies;         // 처리된 기업 수
    private int successCount;               // 성공 수
    private int failureCount;               // 실패 수
    private double progressPercentage;      // 진행률 (%)
    private long estimatedTimeRemaining;    // 예상 남은 시간 (초)
    private long elapsedTime;               // 경과 시간 (초)
}
package com.expensemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for leave request analytics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveAnalyticsDto {
    private Long totalLeaves;
    private Long pendingLeaves;
    private Long approvedLeaves;
    private Long rejectedLeaves;
    private Long cancelledLeaves;
    private List<LeaveTypeAnalyticsDto> leavesByType;
    private List<MonthlyLeaveDto> leavesByMonth;
    private Map<String, Long> leavesByStatus;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaveTypeAnalyticsDto {
        private String leaveType;
        private Long count;
        private Long totalDays;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyLeaveDto {
        private String month;
        private Long count;
        private Long totalDays;
    }
}


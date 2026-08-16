package com.expensemanagement.service;

import com.expensemanagement.dto.LeaveAnalyticsDto;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for leave request analytics.
 * Provides leave analytics with caching support for improved performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveAnalyticsService {
    
    private final LeaveRequestRepository leaveRequestRepository;
    
    /**
     * Get analytics for leave requests within a date range.
     * Results are cached for 5 minutes to improve performance.
     * 
     * @param organizationId The organization ID
     * @param startDate Start date (defaults to 6 months ago if null)
     * @param endDate End date (defaults to today if null)
     * @return LeaveAnalyticsDto containing leave analytics
     */
    @Cacheable(value = "analytics", key = "'leave_' + #organizationId + '_' + (#startDate != null ? #startDate.toString() : 'null') + '_' + (#endDate != null ? #endDate.toString() : 'null')")
    public LeaveAnalyticsDto getLeaveAnalytics(Long organizationId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        List<LeaveRequest> leaves = leaveRequestRepository.findByOrganizationIdAndDateRange(
                organizationId, startDate, endDate);
        
        LeaveAnalyticsDto.LeaveAnalyticsDtoBuilder builder = LeaveAnalyticsDto.builder();
        
        // Calculate totals by status
        long totalLeaves = leaves.size();
        long pendingLeaves = leaves.stream()
                .filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.PENDING)
                .count();
        long approvedLeaves = leaves.stream()
                .filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED)
                .count();
        long rejectedLeaves = leaves.stream()
                .filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.REJECTED)
                .count();
        long cancelledLeaves = leaves.stream()
                .filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.CANCELLED)
                .count();
        
        // Leaves by type
        Map<LeaveRequest.LeaveType, Long> typeCounts = new HashMap<>();
        Map<LeaveRequest.LeaveType, Long> typeDays = new HashMap<>();
        
        for (LeaveRequest leave : leaves) {
            typeCounts.merge(leave.getLeaveType(), 1L, Long::sum);
            typeDays.merge(leave.getLeaveType(), (long) leave.getNumberOfDays(), Long::sum);
        }
        
        List<LeaveAnalyticsDto.LeaveTypeAnalyticsDto> leavesByType = typeCounts.entrySet().stream()
                .map(entry -> {
                    LeaveAnalyticsDto.LeaveTypeAnalyticsDto dto = new LeaveAnalyticsDto.LeaveTypeAnalyticsDto();
                    dto.setLeaveType(entry.getKey().name());
                    dto.setCount(entry.getValue());
                    dto.setTotalDays(typeDays.getOrDefault(entry.getKey(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());
        
        // Leaves by month
        Map<String, Long> monthlyCounts = new HashMap<>();
        Map<String, Long> monthlyDays = new HashMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        
        for (LeaveRequest leave : leaves) {
            String month = leave.getStartDate().format(monthFormatter);
            monthlyCounts.merge(month, 1L, Long::sum);
            monthlyDays.merge(month, (long) leave.getNumberOfDays(), Long::sum);
        }
        
        List<LeaveAnalyticsDto.MonthlyLeaveDto> leavesByMonth = monthlyCounts.entrySet().stream()
                .map(entry -> {
                    LeaveAnalyticsDto.MonthlyLeaveDto dto = new LeaveAnalyticsDto.MonthlyLeaveDto();
                    dto.setMonth(entry.getKey());
                    dto.setCount(entry.getValue());
                    dto.setTotalDays(monthlyDays.getOrDefault(entry.getKey(), 0L));
                    return dto;
                })
                .sorted((a, b) -> a.getMonth().compareTo(b.getMonth()))
                .collect(Collectors.toList());
        
        // Leaves by status
        Map<String, Long> leavesByStatus = new HashMap<>();
        leavesByStatus.put("PENDING", pendingLeaves);
        leavesByStatus.put("APPROVED", approvedLeaves);
        leavesByStatus.put("REJECTED", rejectedLeaves);
        leavesByStatus.put("CANCELLED", cancelledLeaves);
        
        return builder
                .totalLeaves(totalLeaves)
                .pendingLeaves(pendingLeaves)
                .approvedLeaves(approvedLeaves)
                .rejectedLeaves(rejectedLeaves)
                .cancelledLeaves(cancelledLeaves)
                .leavesByType(leavesByType)
                .leavesByMonth(leavesByMonth)
                .leavesByStatus(leavesByStatus)
                .build();
    }
}


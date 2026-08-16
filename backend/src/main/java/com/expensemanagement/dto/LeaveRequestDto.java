package com.expensemanagement.dto;

import com.expensemanagement.model.LeaveRequest;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for leave request operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestDto {
    private Long id;
    private Long userId;
    private String userName;
    private Long organizationId;
    
    @NotNull(message = "Leave type is required")
    private LeaveRequest.LeaveType leaveType;
    
    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date cannot be in the past")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required")
    @FutureOrPresent(message = "End date cannot be in the past")
    private LocalDate endDate;
    
    private Integer numberOfDays; // Calculated from working days, not directly provided
    
    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
    private String reason;
    
    private LeaveRequest.LeaveStatus status;
    private Long approvedById;
    private String approvedByName;
    private String approvalComments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    
    // Paid/Unpaid leave tracking
    private Boolean isPaid;
    private Integer paidDays;
    private Integer unpaidDays;
    
    // Leave balance info (included in responses)
    private Map<String, Object> leaveBalance;
}


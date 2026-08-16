package com.expensemanagement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetDto {
    private Long id;
    private Long userId;
    private String userName;
    private Long projectId;
    
    @Size(max = 50, message = "Project code cannot exceed 50 characters")
    private String projectCode; // Can be null for leave entries
    
    private String projectName;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    @DecimalMin(value = "0.0", message = "Hours cannot be negative")
    @DecimalMax(value = "24.0", message = "Hours cannot exceed 24 in a day")
    private Double hours; // 0 for leave entries
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    private String status;
    private Long organizationId;
    private Long approvedBy;
    private String approvedByName;
    private String approvalComments;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Leave integration fields
    private String entryType; // WORK, LEAVE, HOLIDAY
    private String leaveType; // VACATION, SICK_LEAVE, etc. (only for LEAVE entries)
    private Boolean isPaidLeave; // Whether the leave is paid (only for LEAVE entries)
    private Boolean isEditable; // Whether user can edit this entry (false for approved leaves)
    private Long leaveRequestId; // Reference to leave request (only for LEAVE entries)
}

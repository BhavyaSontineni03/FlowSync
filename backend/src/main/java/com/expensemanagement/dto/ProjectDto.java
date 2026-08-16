package com.expensemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class ProjectDto {
    private Long id;
    
    @NotBlank(message = "Project code is required")
    @Size(max = 50, message = "Project code cannot exceed 50 characters")
    private String code;
    
    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Project name cannot exceed 200 characters")
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
    
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    private LocalDate endDate;
    
    @NotBlank(message = "Status is required")
    @Pattern(
            regexp = "ACTIVE|INACTIVE|COMPLETED|ON_HOLD",
            message = "Status must be one of ACTIVE, INACTIVE, COMPLETED, ON_HOLD"
    )
    private String status;
    
    private Long organizationId;
    
    /**
     * The ID of the manager responsible for this project.
     * This user must have the MANAGER role.
     */
    private Long managerId;
    
    /**
     * The name of the project manager (read-only, populated from backend).
     */
    private String managerName;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

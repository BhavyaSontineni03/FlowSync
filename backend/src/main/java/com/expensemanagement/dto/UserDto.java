package com.expensemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private Long organizationId;
    private Long managerId;
    private String managerName;
    private Long hrId;
    private String hrName;
    private Boolean isOnBench;
    private BigDecimal monthlySalary;
    private String phoneNumber;
    private String address;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

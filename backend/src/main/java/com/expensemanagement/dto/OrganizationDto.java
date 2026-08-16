package com.expensemanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDto {
    private Long id;
    
    @NotBlank(message = "Organization name is required")
    private String name;
    
    @NotBlank(message = "Subdomain is required")
    private String subdomain;
    
    @NotBlank(message = "Address is required")
    private String address;
    
    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email should be valid")
    private String contactEmail;
    
    @NotBlank(message = "Contact phone is required")
    private String contactPhone;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


package com.expensemanagement.controller;

import com.expensemanagement.dto.OrganizationDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    
    private final OrganizationService organizationService;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping("/register")
    public ResponseEntity<OrganizationDto> registerOrganization(@Valid @RequestBody OrganizationDto dto) {
        OrganizationDto created = organizationService.createOrganization(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDto> getOrganizationById(
            @PathVariable Long id,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userOrganizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        OrganizationDto organization = organizationService.getOrganizationById(id);
        
        // Users can only view their own organization
        if (!organization.getId().equals(userOrganizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(organization);
    }
    
    @GetMapping("/subdomain/{subdomain}")
    public ResponseEntity<OrganizationDto> getOrganizationBySubdomain(@PathVariable String subdomain) {
        OrganizationDto organization = organizationService.getOrganizationBySubdomain(subdomain);
        return ResponseEntity.ok(organization);
    }
    
    @GetMapping
    public ResponseEntity<List<OrganizationDto>> getAllOrganizations(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only ADMIN can view all organizations
        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<OrganizationDto> organizations = organizationService.getAllOrganizations();
        return ResponseEntity.ok(organizations);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}


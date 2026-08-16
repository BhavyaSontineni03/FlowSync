package com.expensemanagement.service;

import com.expensemanagement.dto.OrganizationDto;
import com.expensemanagement.model.Organization;
import com.expensemanagement.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {
    
    private final OrganizationRepository organizationRepository;
    private final ActivityLogService activityLogService;
    
    @Transactional
    public OrganizationDto createOrganization(OrganizationDto dto) {
        if (organizationRepository.existsBySubdomain(dto.getSubdomain())) {
            throw new RuntimeException("Subdomain already exists");
        }
        
        if (organizationRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Organization name already exists");
        }
        
        Organization organization = Organization.builder()
                .name(dto.getName())
                .subdomain(dto.getSubdomain())
                .address(dto.getAddress())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .build();
        
        organization = organizationRepository.save(organization);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.ORGANIZATION_CREATED,
                "Organization created: " + organization.getName(),
                organization
        );
        
        return convertToDto(organization);
    }
    
    public OrganizationDto getOrganizationById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        return convertToDto(organization);
    }
    
    public OrganizationDto getOrganizationBySubdomain(String subdomain) {
        Organization organization = organizationRepository.findBySubdomain(subdomain)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        return convertToDto(organization);
    }
    
    public List<OrganizationDto> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public OrganizationDto updateOrganization(Long id, OrganizationDto dto) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        if (!organization.getSubdomain().equals(dto.getSubdomain()) && 
            organizationRepository.existsBySubdomain(dto.getSubdomain())) {
            throw new RuntimeException("Subdomain already exists");
        }
        
        organization.setName(dto.getName());
        organization.setSubdomain(dto.getSubdomain());
        organization.setAddress(dto.getAddress());
        organization.setContactEmail(dto.getContactEmail());
        organization.setContactPhone(dto.getContactPhone());
        
        organization = organizationRepository.save(organization);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.ORGANIZATION_UPDATED,
                "Organization updated: " + organization.getName(),
                organization
        );
        
        return convertToDto(organization);
    }
    
    private OrganizationDto convertToDto(Organization organization) {
        return OrganizationDto.builder()
                .id(organization.getId())
                .name(organization.getName())
                .subdomain(organization.getSubdomain())
                .address(organization.getAddress())
                .contactEmail(organization.getContactEmail())
                .contactPhone(organization.getContactPhone())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }
}


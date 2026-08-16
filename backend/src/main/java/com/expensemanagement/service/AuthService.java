package com.expensemanagement.service;

import com.expensemanagement.dto.AuthRequest;
import com.expensemanagement.dto.AuthResponse;
import com.expensemanagement.dto.RegisterRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final ActivityLogService activityLogService;
    
    @Transactional
    public AuthResponse login(AuthRequest request) {
        Organization organization = null;
        if (request.getOrganizationSubdomain() != null && !request.getOrganizationSubdomain().isEmpty()) {
            organization = organizationRepository.findBySubdomain(request.getOrganizationSubdomain())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
        }
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (organization != null && !user.getOrganization().getId().equals(organization.getId())) {
            throw new RuntimeException("User does not belong to this organization");
        }
        
        String token = tokenProvider.generateToken(authentication, user.getId(), 
                user.getOrganization().getId(), user.getRole().name());
        
        // Note: Login events are not logged to activity logs as they are too frequent
        // Only significant actions like expense operations, approvals, etc. are logged
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .organizationId(user.getOrganization().getId())
                .organizationName(user.getOrganization().getName())
                .build();
    }
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        Organization organization = resolveOrganization(request);
        
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : User.UserRole.EMPLOYEE)
                .organization(organization)
                .enabled(true)
                .build();
        
        user = userRepository.save(user);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.USER_CREATED,
                "New user registered: " + user.getEmail(),
                user,
                organization,
                "User",
                user.getId(),
                null
        );
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        String token = tokenProvider.generateToken(authentication, user.getId(), 
                organization.getId(), user.getRole().name());
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .build();
    }

    private Organization resolveOrganization(RegisterRequest request) {
        if (request.getOrganizationSubdomain() != null && !request.getOrganizationSubdomain().isBlank()) {
            return organizationRepository.findBySubdomain(request.getOrganizationSubdomain().trim())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
        }
        if (request.getOrganizationId() != null) {
            return organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
        }
        throw new RuntimeException("Organization subdomain is required");
    }
}


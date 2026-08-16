package com.expensemanagement.controller;

import com.expensemanagement.dto.UserDto;
import com.expensemanagement.dto.UserUpsertRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.AdminRequestRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.ProjectAssignmentRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for user management operations.
 * Only Admin can manage users.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserManagementController {
    
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.expensemanagement.service.UserServiceFacade userServiceFacade;
    
    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Valid @RequestBody UserUpsertRequest request,
            HttpServletRequest servletRequest) {
        
        AuthContext ctx = auth(servletRequest);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userServiceFacade.createUser(request, ctx.organizationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(user));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disableUser(@PathVariable Long id, HttpServletRequest request) {
        AuthContext ctx = auth(request);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getOrganization().getId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        user.setEnabled(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpsertRequest request,
            HttpServletRequest servletRequest) {
        
        AuthContext ctx = auth(servletRequest);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = userServiceFacade.updateUser(id, request, ctx.organizationId());
        return ResponseEntity.ok(convertToDto(user));
    }
    
    @GetMapping
    public ResponseEntity<Page<UserDto>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "firstName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            HttpServletRequest request) {
        
        AuthContext ctx = auth(request);
        
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<User> users = userRepository.findByOrganizationId(ctx.organizationId(), pageable);
        return ResponseEntity.ok(users.map(this::convertToDto));
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpServletRequest request) {
        AuthContext ctx = auth(request);
        
        if (!(ctx.isAdmin() || ctx.isHr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<User> users = userRepository.findByOrganizationId(ctx.organizationId());
        return ResponseEntity.ok(users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }
    
    /**
     * Get team members who are assigned to projects managed by the current user.
     * This is DYNAMIC - based on project assignments, not a static manager field.
     * An employee's manager is determined by which project they're working on.
     */
    @GetMapping("/my-team")
    public ResponseEntity<List<UserDto>> getMyTeam(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        // Find all active assignments for projects managed by this user
        List<com.expensemanagement.model.ProjectAssignment> assignments = 
            projectAssignmentRepository.findActiveAssignmentsByProjectManager(userId);
        
        // Extract unique users from assignments
        List<User> teamMembers = assignments.stream()
                .map(com.expensemanagement.model.ProjectAssignment::getUser)
                .distinct()
                .filter(User::getEnabled)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(teamMembers.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }
    
    /**
     * Get employees assigned to the current HR.
     * HR has a static relationship with employees (hr_id field on User).
     * This is constant regardless of which projects the employee is on.
     */
    @GetMapping("/my-employees")
    public ResponseEntity<List<UserDto>> getMyEmployees(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        // Only HR can access this endpoint
        if (!"HR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Find all enabled employees assigned to this HR
        List<User> employees = userRepository.findByHrIdAndEnabled(userId, true);
        
        return ResponseEntity.ok(employees.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));
    }
    
    @PutMapping("/{id}/manager")
    public ResponseEntity<UserDto> assignManager(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            HttpServletRequest request) {
        
        AuthContext ctx = auth(request);
        
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getOrganization().getId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long managerId = body.get("managerId");
        if (managerId != null) {
            User manager = userRepository.findById(managerId)
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
            if (!manager.getOrganization().getId().equals(ctx.organizationId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (manager.getRole() != User.UserRole.MANAGER && manager.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            user.setManager(manager);
        } else {
            user.setManager(null);
        }
        
        user = userRepository.save(user);
        return ResponseEntity.ok(convertToDto(user));
    }
    
    @PutMapping("/{id}/hr")
    public ResponseEntity<UserDto> assignHR(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            HttpServletRequest request) {
        
        AuthContext ctx = auth(request);
        
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getOrganization().getId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Long hrId = body.get("hrId");
        if (hrId != null) {
            User hr = userRepository.findById(hrId)
                    .orElseThrow(() -> new RuntimeException("HR not found"));
            if (!hr.getOrganization().getId().equals(ctx.organizationId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (hr.getRole() != User.UserRole.HR && hr.getRole() != User.UserRole.ADMIN) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            user.setHr(hr);
        } else {
            user.setHr(null);
        }
        
        user = userRepository.save(user);
        return ResponseEntity.ok(convertToDto(user));
    }
    
    @PutMapping("/{id}/salary")
    public ResponseEntity<UserDto> updateSalary(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        
        AuthContext ctx = auth(request);
        
        if (!(ctx.isAdmin() || ctx.isHr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getOrganization().getId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Object salaryObj = body.get("monthlySalary");
        if (salaryObj != null) {
            BigDecimal salary = new BigDecimal(salaryObj.toString());
            user.setMonthlySalary(salary);
        } else {
            user.setMonthlySalary(null);
        }
        
        user = userRepository.save(user);
        return ResponseEntity.ok(convertToDto(user));
    }
    
    /**
     * Get current user's profile
     */
    @GetMapping("/profile")
    public ResponseEntity<UserDto> getMyProfile(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(convertToDto(user));
    }
    
    /**
     * Submit profile update request to admin.
     * Users can request changes to: firstName, lastName, phoneNumber, address
     * These changes require admin approval.
     */
    @PostMapping("/profile/update-request")
    public ResponseEntity<?> requestProfileUpdate(
            @RequestBody Map<String, String> updates,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Create profile update request
        // Only allow certain fields to be updated
        Map<String, String> allowedUpdates = new java.util.HashMap<>();
        if (updates.containsKey("firstName")) allowedUpdates.put("firstName", updates.get("firstName"));
        if (updates.containsKey("lastName")) allowedUpdates.put("lastName", updates.get("lastName"));
        if (updates.containsKey("phoneNumber")) allowedUpdates.put("phoneNumber", updates.get("phoneNumber"));
        if (updates.containsKey("address")) allowedUpdates.put("address", updates.get("address"));
        
        if (allowedUpdates.isEmpty()) {
            return ResponseEntity.badRequest().body("No valid fields to update");
        }
        
        // Include current user ID in payload
        allowedUpdates.put("userId", userId.toString());
        
        try {
            String payloadJson = objectMapper.writeValueAsString(allowedUpdates);
            
            Organization org = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            
            com.expensemanagement.model.AdminRequest adminRequest = com.expensemanagement.model.AdminRequest.builder()
                    .type(com.expensemanagement.model.AdminRequest.RequestType.PROFILE_UPDATE)
                    .status(com.expensemanagement.model.AdminRequest.RequestStatus.PENDING)
                    .requestedBy(user)
                    .organizationId(organizationId)
                    .createdAt(java.time.LocalDateTime.now())
                    .payloadJson(payloadJson)
                    .description("Profile update request from " + user.getFirstName() + " " + user.getLastName())
                    .build();
            
            // Save using the admin request repository
            adminRequest = adminRequestRepository.save(adminRequest);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Profile update request submitted successfully",
                    "requestId", adminRequest.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error submitting request: " + e.getMessage());
        }
    }
    
    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .organizationId(user.getOrganization().getId())
                .managerId(user.getManager() != null ? user.getManager().getId() : null)
                .managerName(user.getManager() != null ? 
                        user.getManager().getFirstName() + " " + user.getManager().getLastName() : null)
                .hrId(user.getHr() != null ? user.getHr().getId() : null)
                .hrName(user.getHr() != null ? 
                        user.getHr().getFirstName() + " " + user.getHr().getLastName() : null)
                .isOnBench(user.getIsOnBench())
                .monthlySalary(user.getMonthlySalary())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private record AuthContext(Long organizationId, String role) {
        boolean isAdmin() { return "ADMIN".equals(role); }
        boolean isHr() { return "HR".equals(role); }
    }

    private AuthContext auth(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long orgId = tokenProvider.getOrganizationIdFromToken(token);
        String role = tokenProvider.getRoleFromToken(token);
        return new AuthContext(orgId, role);
    }

    private User resolveAssignee(Long id, Long organizationId, Set<User.UserRole> allowedRoles) {
        if (id == null) {
            return null;
        }
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Assignee must belong to organization");
        }
        if (!allowedRoles.contains(user.getRole())) {
            throw new RuntimeException("Assignee role not permitted");
        }
        return user;
    }
}

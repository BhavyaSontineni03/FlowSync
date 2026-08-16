package com.expensemanagement.controller;

import com.expensemanagement.dto.ProjectDto;
import com.expensemanagement.dto.UserUpsertRequest;
import com.expensemanagement.model.AdminRequest;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.AdminRequestRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.ProjectAssignmentService;
import com.expensemanagement.service.ProjectService;
import com.expensemanagement.service.UserServiceFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin-requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestRepository adminRequestRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final UserServiceFacade userServiceFacade;
    private final ProjectService projectService;
    private final ProjectAssignmentService projectAssignmentService;

    @PostMapping("/user")
    public ResponseEntity<AdminRequest> requestUserChange(
            @Valid @RequestBody UserRequestPayload payload,
            HttpServletRequest request) {

        AuthContext ctx = auth(request);
        if (!(ctx.isAdmin() || ctx.isManager() || ctx.isHr() || ctx.isFinance())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        payload.organizationId = ctx.organizationId();
        AdminRequest adminRequest = AdminRequest.builder()
                .type(payload.type)
                .status(AdminRequest.RequestStatus.PENDING)
                .organizationId(ctx.organizationId())
                .requestedBy(userRepository.findById(ctx.userId()).orElseThrow())
                .createdAt(LocalDateTime.now())
                .payloadJson(payload.toJson(objectMapper))
                .build();

        adminRequest = adminRequestRepository.save(adminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRequest);
    }

    @PostMapping("/project")
    public ResponseEntity<AdminRequest> requestProjectChange(
            @Valid @RequestBody ProjectRequestPayload payload,
            HttpServletRequest request) {

        AuthContext ctx = auth(request);
        if (!(ctx.isAdmin() || ctx.isManager() || ctx.isHr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        payload.organizationId = ctx.organizationId();
        AdminRequest adminRequest = AdminRequest.builder()
                .type(payload.type)
                .status(AdminRequest.RequestStatus.PENDING)
                .organizationId(ctx.organizationId())
                .requestedBy(userRepository.findById(ctx.userId()).orElseThrow())
                .createdAt(LocalDateTime.now())
                .payloadJson(payload.toJson(objectMapper))
                .build();

        adminRequest = adminRequestRepository.save(adminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRequest);
    }

    /**
     * Request to assign or unassign an employee to/from a project.
     * Managers can request assignments for their managed projects.
     * HR can request assignments for any project.
     */
    @PostMapping("/assignment")
    public ResponseEntity<AdminRequest> requestAssignmentChange(
            @Valid @RequestBody AssignmentRequestPayload payload,
            HttpServletRequest request) {

        AuthContext ctx = auth(request);
        if (!(ctx.isAdmin() || ctx.isManager() || ctx.isHr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        payload.organizationId = ctx.organizationId();
        AdminRequest adminRequest = AdminRequest.builder()
                .type(payload.type)
                .status(AdminRequest.RequestStatus.PENDING)
                .organizationId(ctx.organizationId())
                .requestedBy(userRepository.findById(ctx.userId()).orElseThrow())
                .createdAt(LocalDateTime.now())
                .payloadJson(payload.toJson(objectMapper))
                .build();

        adminRequest = adminRequestRepository.save(adminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminRequest);
    }

    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminRequest>> getPending(HttpServletRequest request) {
        AuthContext ctx = auth(request);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(adminRequestRepository.findByOrganizationIdAndStatus(
                ctx.organizationId(), AdminRequest.RequestStatus.PENDING));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        AuthContext ctx = auth(request);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AdminRequest adminRequest = adminRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!adminRequest.getOrganizationId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (adminRequest.getStatus() != AdminRequest.RequestStatus.PENDING) {
            return ResponseEntity.badRequest().build();
        }

        execute(adminRequest, ctx.userId());
        adminRequest.setStatus(AdminRequest.RequestStatus.APPROVED);
        adminRequest.setApprovedBy(userRepository.findById(ctx.userId()).orElseThrow());
        adminRequest.setUpdatedAt(LocalDateTime.now());
        adminRequestRepository.save(adminRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, HttpServletRequest request, @RequestBody(required = false) RejectPayload payload) {
        AuthContext ctx = auth(request);
        if (!ctx.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        AdminRequest adminRequest = adminRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!adminRequest.getOrganizationId().equals(ctx.organizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (adminRequest.getStatus() != AdminRequest.RequestStatus.PENDING) {
            return ResponseEntity.badRequest().build();
        }
        adminRequest.setStatus(AdminRequest.RequestStatus.REJECTED);
        adminRequest.setApprovedBy(userRepository.findById(ctx.userId()).orElseThrow());
        adminRequest.setUpdatedAt(LocalDateTime.now());
        adminRequest.setComments(payload != null ? payload.comments : null);
        adminRequestRepository.save(adminRequest);
        return ResponseEntity.ok().build();
    }

    private void execute(AdminRequest request, Long adminId) {
        try {
            switch (request.getType()) {
                case USER_CREATE -> {
                    UserRequestPayload p = objectMapper.readValue(request.getPayloadJson(), UserRequestPayload.class);
                    userServiceFacade.createUser(p.user, p.organizationId);
                }
                case USER_UPDATE -> {
                    UserRequestPayload p = objectMapper.readValue(request.getPayloadJson(), UserRequestPayload.class);
                    userServiceFacade.updateUser(p.id, p.user, p.organizationId);
                }
                case USER_DISABLE -> {
                    UserRequestPayload p = objectMapper.readValue(request.getPayloadJson(), UserRequestPayload.class);
                    userServiceFacade.disableUser(p.id, p.organizationId);
                }
                case PROJECT_CREATE -> {
                    ProjectRequestPayload p = objectMapper.readValue(request.getPayloadJson(), ProjectRequestPayload.class);
                    projectService.createProjectInternal(p.project, p.organizationId);
                }
                case PROJECT_UPDATE -> {
                    ProjectRequestPayload p = objectMapper.readValue(request.getPayloadJson(), ProjectRequestPayload.class);
                    projectService.updateProjectInternal(p.id, p.project, p.organizationId);
                }
                case PROJECT_DELETE -> {
                    ProjectRequestPayload p = objectMapper.readValue(request.getPayloadJson(), ProjectRequestPayload.class);
                    projectService.deleteProjectInternal(p.id, p.organizationId);
                }
                case PROJECT_ASSIGN -> {
                    AssignmentRequestPayload p = objectMapper.readValue(request.getPayloadJson(), AssignmentRequestPayload.class);
                    projectAssignmentService.assignEmployeeToProject(p.userId, p.projectId, p.role, p.organizationId);
                }
                case PROJECT_UNASSIGN -> {
                    AssignmentRequestPayload p = objectMapper.readValue(request.getPayloadJson(), AssignmentRequestPayload.class);
                    projectAssignmentService.unassignEmployeeFromProject(p.userId, p.projectId, p.organizationId);
                }
                case PROFILE_UPDATE -> {
                    // Parse the profile update payload
                    @SuppressWarnings("unchecked")
                    Map<String, String> updates = objectMapper.readValue(request.getPayloadJson(), Map.class);
                    Long userId = Long.valueOf(updates.get("userId"));
                    
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    
                    // Apply the allowed updates
                    if (updates.containsKey("firstName") && updates.get("firstName") != null) {
                        user.setFirstName(updates.get("firstName"));
                    }
                    if (updates.containsKey("lastName") && updates.get("lastName") != null) {
                        user.setLastName(updates.get("lastName"));
                    }
                    if (updates.containsKey("phoneNumber")) {
                        user.setPhoneNumber(updates.get("phoneNumber"));
                    }
                    if (updates.containsKey("address")) {
                        user.setAddress(updates.get("address"));
                    }
                    
                    userRepository.save(user);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute request: " + e.getMessage(), e);
        }
    }

    private record AuthContext(Long organizationId, String role, Long userId) {
        boolean isAdmin() { return "ADMIN".equals(role); }
        boolean isHr() { return "HR".equals(role); }
        boolean isManager() { return "MANAGER".equals(role); }
        boolean isFinance() { return "FINANCE".equals(role); }
    }

    private AuthContext auth(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long orgId = tokenProvider.getOrganizationIdFromToken(token);
        String role = tokenProvider.getRoleFromToken(token);
        Long userId = tokenProvider.getUserIdFromToken(token);
        return new AuthContext(orgId, role, userId);
    }

    public static class UserRequestPayload {
        public AdminRequest.RequestType type;
        @Valid
        public UserUpsertRequest user;
        public Long id; // for update/disable
        public Long organizationId;

        public String toJson(ObjectMapper mapper) {
            try {
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ProjectRequestPayload {
        public AdminRequest.RequestType type;
        @Valid
        public ProjectDto project;
        public Long id; // for update/delete
        public Long organizationId;

        public String toJson(ObjectMapper mapper) {
            try {
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class RejectPayload {
        public String comments;
    }

    /**
     * Payload for assignment/unassignment requests.
     */
    public static class AssignmentRequestPayload {
        public AdminRequest.RequestType type; // PROJECT_ASSIGN or PROJECT_UNASSIGN
        public Long userId;      // Employee to assign/unassign
        public Long projectId;   // Target project
        public String role;      // Role in the project (for assign)
        public Long organizationId;

        public String toJson(ObjectMapper mapper) {
            try {
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

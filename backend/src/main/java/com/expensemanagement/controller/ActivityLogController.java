package com.expensemanagement.controller;

import com.expensemanagement.dto.ActivityLogDto;
import com.expensemanagement.model.ActivityLog;
import com.expensemanagement.repository.ActivityLogRepository;
import com.expensemanagement.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity-logs")
@RequiredArgsConstructor
public class ActivityLogController {
    
    private final ActivityLogRepository activityLogRepository;
    private final JwtTokenProvider tokenProvider;
    
    @GetMapping
    public ResponseEntity<Page<ActivityLogDto>> getActivityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);
        Long userId = tokenProvider.getUserIdFromToken(token);
        String userRole = tokenProvider.getRoleFromToken(token);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logs;
        
        // Employees can only see their own activity logs
        // Managers, Admins, and Finance can see all organization activity logs
        if ("EMPLOYEE".equals(userRole)) {
            logs = activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            logs = activityLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        }
        
        Page<ActivityLogDto> dtoPage = logs.map(log -> ActivityLogDto.builder()
                .id(log.getId())
                .activityType(log.getActivityType().name())
                .description(log.getDescription())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .user(log.getUser() != null ? ActivityLogDto.UserInfo.builder()
                        .id(log.getUser().getId())
                        .firstName(log.getUser().getFirstName())
                        .lastName(log.getUser().getLastName())
                        .email(log.getUser().getEmail())
                        .build() : null)
                .build());
        
        return ResponseEntity.ok(dtoPage);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}


package com.expensemanagement.controller;

import com.expensemanagement.dto.NotificationDto;
import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationService notificationService;
    private final JwtTokenProvider tokenProvider;
    
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto> notifications = notificationService.getUserNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }
    
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        Long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }
    
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        Long userId = tokenProvider.getUserIdFromToken(token);
        
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}


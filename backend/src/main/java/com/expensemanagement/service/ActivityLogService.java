package com.expensemanagement.service;

import com.expensemanagement.model.ActivityLog;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {
    
    private final ActivityLogRepository activityLogRepository;
    
    @Async
    @Transactional
    public void logActivity(ActivityLog.ActivityType activityType, String description, 
                          User user, Organization organization, String entityType, Long entityId, String metadata) {
        try {
            ActivityLog log = ActivityLog.builder()
                    .activityType(activityType)
                    .description(description)
                    .user(user)
                    .organization(organization)
                    .entityType(entityType)
                    .entityId(entityId)
                    .metadata(metadata)
                    .build();
            
            activityLogRepository.save(log);
        } catch (Exception e) {
            log.error("Error logging activity", e);
        }
    }
    
    @Async
    @Transactional
    public void logActivity(ActivityLog.ActivityType activityType, String description, 
                          Organization organization) {
        logActivity(activityType, description, null, organization, null, null, null);
    }
}


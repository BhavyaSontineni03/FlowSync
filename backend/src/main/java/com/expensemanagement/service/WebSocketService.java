package com.expensemanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotificationToUser(Long userId, Object notification) {
        messagingTemplate.convertAndSend("/queue/notifications/" + userId, notification);
        log.debug("Sent notification to user: {}", userId);
    }

    public void sendNotificationToOrganization(Long organizationId, Object notification) {
        messagingTemplate.convertAndSend("/topic/organization/" + organizationId, notification);
        log.debug("Sent notification to organization: {}", organizationId);
    }

    public void sendExpenseUpdate(Long organizationId, Object expenseUpdate) {
        messagingTemplate.convertAndSend("/topic/expenses/" + organizationId, expenseUpdate);
        log.debug("Sent expense update to organization: {}", organizationId);
    }

    public void sendApprovalUpdate(Long organizationId, Object approvalUpdate) {
        messagingTemplate.convertAndSend("/topic/approvals/" + organizationId, approvalUpdate);
        log.debug("Sent approval update to organization: {}", organizationId);
    }
    
    public void sendPayrollUpdate(Long organizationId, Object payrollUpdate) {
        messagingTemplate.convertAndSend("/topic/payroll/" + organizationId, payrollUpdate);
        log.debug("Sent payroll update to organization: {}", organizationId);
    }
}


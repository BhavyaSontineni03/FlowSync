package com.expensemanagement.service;

import com.expensemanagement.dto.NotificationDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Notification;
import com.expensemanagement.model.Payroll;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.NotificationRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final WebSocketService webSocketService;
    
    @Transactional
    public void notifyManagersForApproval(Expense expense) {
        User assignedManager = expense.getUser().getManager();
        
        // Only notify the employee's assigned manager (who can actually approve)
        if (assignedManager != null) {
            createExpenseApprovalNotification(assignedManager, expense);
        } else {
            // Fallback: If no manager assigned, notify all managers in the organization
            // This allows the expense to still be visible but highlights the assignment issue
            log.warn("Employee {} does not have an assigned manager. Notifying all managers.", 
                    expense.getUser().getEmail());
            List<User> managers = userRepository.findByOrganizationIdAndRoleIn(
                    expense.getOrganization().getId(),
                    List.of(User.UserRole.MANAGER)
            );
            
            for (User manager : managers) {
                createExpenseApprovalNotification(manager, expense);
            }
        }
    }
    
    private void createExpenseApprovalNotification(User manager, Expense expense) {
        Notification notification = Notification.builder()
                .user(manager)
                .title("Expense Approval Required")
                .message(expense.getUser().getFirstName() + " " + expense.getUser().getLastName() + 
                        " submitted an expense: " + expense.getDescription() + " ($" + expense.getAmount() + ")")
                .type(Notification.NotificationType.APPROVAL_REQUESTED)
                .relatedEntityId(expense.getId())
                .relatedEntityType("Expense")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        
        // Send email notification
        emailService.sendExpenseApprovalRequest(
                manager.getEmail(),
                expense.getUser().getFirstName() + " " + expense.getUser().getLastName(),
                expense.getDescription(),
                expense.getAmount().toString()
        );
        
        // Send real-time notification via WebSocket
        webSocketService.sendNotificationToUser(manager.getId(), convertToDto(notification));
    }
    
    @Transactional
    public void notifyExpenseApproved(Expense expense, User approver) {
        Notification notification = Notification.builder()
                .user(expense.getUser())
                .title("Expense Approved")
                .message("Your expense \"" + expense.getDescription() + "\" ($" + expense.getAmount() + 
                        ") has been approved by " + approver.getFirstName() + " " + approver.getLastName())
                .type(Notification.NotificationType.EXPENSE_APPROVED)
                .relatedEntityId(expense.getId())
                .relatedEntityType("Expense")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        
        // Send email notification
        emailService.sendExpenseApproved(
                expense.getUser().getEmail(),
                expense.getDescription(),
                expense.getAmount().toString(),
                approver.getFirstName() + " " + approver.getLastName()
        );
        
        // Send real-time notification via WebSocket
        webSocketService.sendNotificationToUser(expense.getUser().getId(), convertToDto(notification));
    }
    
    @Transactional
    public void notifyExpenseRejected(Expense expense, User approver) {
        Notification notification = Notification.builder()
                .user(expense.getUser())
                .title("Expense Rejected")
                .message("Your expense \"" + expense.getDescription() + "\" ($" + expense.getAmount() + 
                        ") has been rejected by " + approver.getFirstName() + " " + approver.getLastName())
                .type(Notification.NotificationType.EXPENSE_REJECTED)
                .relatedEntityId(expense.getId())
                .relatedEntityType("Expense")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        
        // Send email notification
        emailService.sendExpenseRejected(
                expense.getUser().getEmail(),
                expense.getDescription(),
                expense.getAmount().toString(),
                approver.getFirstName() + " " + approver.getLastName(),
                null // Comments can be added if available
        );
        
        // Send real-time notification via WebSocket
        webSocketService.sendNotificationToUser(expense.getUser().getId(), convertToDto(notification));
    }
    
    public Page<NotificationDto> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::convertToDto);
    }
    
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }
    
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }
    
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
    
    /**
     * Notify managers about a new leave request requiring approval.
     */
    @Transactional
    public void notifyManagersForLeaveApproval(LeaveRequest leaveRequest) {
        List<User> managers = userRepository.findByOrganizationIdAndRoleIn(
                leaveRequest.getOrganization().getId(),
                List.of(User.UserRole.MANAGER, User.UserRole.ADMIN)
        );
        
        for (User manager : managers) {
            Notification notification = Notification.builder()
                    .user(manager)
                    .title("Leave Request Approval Required")
                    .message(leaveRequest.getUser().getFirstName() + " " + leaveRequest.getUser().getLastName() + 
                            " requested " + leaveRequest.getLeaveType() + " leave from " + 
                            leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate())
                    .type(Notification.NotificationType.APPROVAL_REQUESTED)
                    .relatedEntityId(leaveRequest.getId())
                    .relatedEntityType("LeaveRequest")
                    .isRead(false)
                    .build();
            
            notificationRepository.save(notification);
            webSocketService.sendNotificationToUser(manager.getId(), convertToDto(notification));
        }
    }
    
    /**
     * Notify user about leave request approval.
     */
    @Transactional
    public void notifyLeaveApproved(LeaveRequest leaveRequest, User approver) {
        Notification notification = Notification.builder()
                .user(leaveRequest.getUser())
                .title("Leave Request Approved")
                .message("Your " + leaveRequest.getLeaveType() + " leave request from " + 
                        leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate() + 
                        " has been approved by " + approver.getFirstName() + " " + approver.getLastName())
                .type(Notification.NotificationType.EXPENSE_APPROVED) // Reuse type
                .relatedEntityId(leaveRequest.getId())
                .relatedEntityType("LeaveRequest")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        webSocketService.sendNotificationToUser(leaveRequest.getUser().getId(), convertToDto(notification));
    }
    
    /**
     * Notify user about leave request rejection.
     */
    @Transactional
    public void notifyLeaveRejected(LeaveRequest leaveRequest, User approver) {
        Notification notification = Notification.builder()
                .user(leaveRequest.getUser())
                .title("Leave Request Rejected")
                .message("Your " + leaveRequest.getLeaveType() + " leave request from " + 
                        leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate() + 
                        " has been rejected by " + approver.getFirstName() + " " + approver.getLastName())
                .type(Notification.NotificationType.EXPENSE_REJECTED) // Reuse type
                .relatedEntityId(leaveRequest.getId())
                .relatedEntityType("LeaveRequest")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        webSocketService.sendNotificationToUser(leaveRequest.getUser().getId(), convertToDto(notification));
    }
    
    @Transactional
    public void notifyExpensePaid(Expense expense, User financeUser) {
        Notification notification = Notification.builder()
                .user(expense.getUser())
                .title("Expense Paid")
                .message("Your expense \"" + expense.getDescription() + "\" ($" + expense.getAmount() + 
                        ") has been marked as paid by Finance team")
                .type(Notification.NotificationType.EXPENSE_PAID)
                .relatedEntityId(expense.getId())
                .relatedEntityType("Expense")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        
        // Send email notification
        emailService.sendExpensePaid(
                expense.getUser().getEmail(),
                expense.getDescription(),
                expense.getAmount().toString()
        );
        
        // Send real-time notification via WebSocket
        webSocketService.sendNotificationToUser(expense.getUser().getId(), convertToDto(notification));
    }
    
    @Transactional
    public void notifyManagersForTimesheetApproval(com.expensemanagement.model.Timesheet timesheet) {
        User manager = timesheet.getUser().getManager();
        if (manager == null) {
            // If no manager, notify all managers in organization
            List<User> managers = userRepository.findByOrganizationIdAndRoleIn(
                    timesheet.getOrganization().getId(),
                    List.of(User.UserRole.MANAGER, User.UserRole.ADMIN)
            );
            
            for (User m : managers) {
                createTimesheetNotification(m, timesheet);
            }
        } else {
            createTimesheetNotification(manager, timesheet);
        }
    }
    
    private void createTimesheetNotification(User manager, com.expensemanagement.model.Timesheet timesheet) {
        Notification notification = Notification.builder()
                .user(manager)
                .title("Timesheet Approval Required")
                .message(timesheet.getUser().getFirstName() + " " + timesheet.getUser().getLastName() + 
                        " submitted a timesheet for " + timesheet.getDate() + " - " + timesheet.getProjectCode())
                .type(Notification.NotificationType.APPROVAL_REQUESTED)
                .relatedEntityId(timesheet.getId())
                .relatedEntityType("Timesheet")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        webSocketService.sendNotificationToUser(manager.getId(), convertToDto(notification));
    }
    
    /**
     * Notify user that their payroll has been marked as paid.
     */
    @Transactional
    public void notifyPayrollPaid(com.expensemanagement.model.Payroll payroll, User financeUser) {
        Notification notification = Notification.builder()
                .user(payroll.getUser())
                .title("Payroll Paid")
                .message("Your payroll for " + getMonthName(payroll.getPeriodMonth()) + " " + payroll.getPeriodYear() + 
                        " (Net: $" + payroll.getNetSalary() + ") has been marked as paid by Finance team")
                .type(Notification.NotificationType.PAYROLL_PAID)
                .relatedEntityId(payroll.getId())
                .relatedEntityType("Payroll")
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        
        // Send email notification (if email service supports payroll)
        // emailService.sendPayrollPaid(...);
        
        // Send real-time notification via WebSocket
        webSocketService.sendNotificationToUser(payroll.getUser().getId(), convertToDto(notification));
    }
    
    private String getMonthName(Integer month) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        return months[month - 1];
    }
    
    private NotificationDto convertToDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.getIsRead())
                .relatedEntityId(notification.getRelatedEntityId())
                .relatedEntityType(notification.getRelatedEntityType())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}


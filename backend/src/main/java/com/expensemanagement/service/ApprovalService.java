package com.expensemanagement.service;

import com.expensemanagement.dto.ApprovalDto;
import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.EventType;
import com.expensemanagement.model.Approval;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ApprovalRepository;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {
    
    private final ApprovalRepository approvalRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final EventPublisher eventPublisher;
    
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public ApprovalDto approveExpense(Long expenseId, Long approverId, String comments) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        
        // Only MANAGER can approve expenses
        if (approver.getRole() != User.UserRole.MANAGER) {
            throw new RuntimeException("Only managers can approve expenses");
        }
        
        User employee = expense.getUser();
        
        // Prevent self-approval
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot approve your own expense.");
        }
        
        // ONLY the employee's assigned manager can approve expenses
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can approve this expense.");
        }
        
        // Create or update approval (use findFirst to handle any duplicate records)
        Approval approval = approvalRepository.findFirstByExpenseIdAndApproverIdOrderByCreatedAtDesc(expenseId, approverId)
                .orElse(Approval.builder()
                        .expense(expense)
                        .approver(approver)
                        .status(Approval.ApprovalStatus.PENDING)
                        .build());
        
        approval.setStatus(Approval.ApprovalStatus.APPROVED);
        approval.setComments(comments);
        approval = approvalRepository.save(approval);
        
        // Check if all required approvals are done (for now, one approval is enough)
        expense.setStatus(Expense.ExpenseStatus.APPROVED);
        expenseRepository.save(expense);

        // Kicks off the payment saga (budget reservation + payment
        // notification, with compensation if either fails) once this
        // transaction commits.
        eventPublisher.publish(EventType.EXPENSE_APPROVED, "Expense", expense.getId(), expense.getOrganization().getId(),
                java.util.Map.of("approverId", approver.getId(), "amount", expense.getAmount().toString()));

        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_APPROVED,
                "Expense approved: " + expense.getDescription() + " by " + approver.getFirstName() + " " + approver.getLastName(),
                approver,
                expense.getOrganization(),
                "Expense",
                expense.getId(),
                comments
        );
        
        notificationService.notifyExpenseApproved(expense, approver);
        
        // Send real-time update via WebSocket
        webSocketService.sendExpenseUpdate(expense.getOrganization().getId(), 
                com.expensemanagement.dto.ExpenseDto.builder()
                        .id(expense.getId())
                        .status(expense.getStatus())
                        .build());
        webSocketService.sendApprovalUpdate(expense.getOrganization().getId(), convertToDto(approval));
        
        return convertToDto(approval);
    }
    
    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public ApprovalDto rejectExpense(Long expenseId, Long approverId, String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new RuntimeException("Rejection comments are required");
        }
        
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        
        // Only MANAGER can reject expenses
        if (approver.getRole() != User.UserRole.MANAGER) {
            throw new RuntimeException("Only managers can reject expenses");
        }
        
        User employee = expense.getUser();
        
        // Prevent self-rejection
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot reject your own expense.");
        }
        
        // ONLY the employee's assigned manager can reject expenses
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can reject this expense.");
        }
        
        // Use findFirst to handle any duplicate records
        Approval approval = approvalRepository.findFirstByExpenseIdAndApproverIdOrderByCreatedAtDesc(expenseId, approverId)
                .orElse(Approval.builder()
                        .expense(expense)
                        .approver(approver)
                        .status(Approval.ApprovalStatus.PENDING)
                        .build());
        
        approval.setStatus(Approval.ApprovalStatus.REJECTED);
        approval.setComments(comments);
        approval = approvalRepository.save(approval);
        
        expense.setStatus(Expense.ExpenseStatus.REJECTED);
        expenseRepository.save(expense);

        eventPublisher.publish(EventType.EXPENSE_REJECTED, "Expense", expense.getId(), expense.getOrganization().getId(),
                java.util.Map.of("approverId", approver.getId(), "reason", comments));

        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_REJECTED,
                "Expense rejected: " + expense.getDescription() + " by " + approver.getFirstName() + " " + approver.getLastName(),
                approver,
                expense.getOrganization(),
                "Expense",
                expense.getId(),
                comments
        );
        
        notificationService.notifyExpenseRejected(expense, approver);
        
        // Send real-time update via WebSocket
        webSocketService.sendExpenseUpdate(expense.getOrganization().getId(), 
                com.expensemanagement.dto.ExpenseDto.builder()
                        .id(expense.getId())
                        .status(expense.getStatus())
                        .build());
        webSocketService.sendApprovalUpdate(expense.getOrganization().getId(), convertToDto(approval));
        
        return convertToDto(approval);
    }
    
    public List<ApprovalDto> getPendingApprovals(Long managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        
        // Get all SUBMITTED expenses from employees where the current user is their assigned manager
        List<Expense> submittedExpenses = expenseRepository.findByUserManagerIdAndStatusOrderByExpenseDateDesc(
                managerId, Expense.ExpenseStatus.SUBMITTED);
        
        // Convert to ApprovalDto, creating approval records if they don't exist
        return submittedExpenses.stream()
                .map(expense -> {
                    // Check if approval record exists (use findFirst to handle any duplicate records)
                    Approval approval = approvalRepository.findFirstByExpenseIdAndApproverIdOrderByCreatedAtDesc(expense.getId(), managerId)
                            .orElse(Approval.builder()
                                    .expense(expense)
                                    .approver(manager)
                                    .status(Approval.ApprovalStatus.PENDING)
                                    .build());
                    
                    // Only return if status is PENDING (not yet approved/rejected)
                    if (approval.getStatus() == Approval.ApprovalStatus.PENDING) {
                        // Save if it's a new approval record
                        if (approval.getId() == null) {
                            approval = approvalRepository.save(approval);
                        }
                        return convertToDto(approval);
                    }
                    return null;
                })
                .filter(approvalDto -> approvalDto != null)
                .collect(Collectors.toList());
    }
    
    public List<ApprovalDto> getExpenseApprovals(Long expenseId) {
        return approvalRepository.findByExpenseId(expenseId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    private ApprovalDto convertToDto(Approval approval) {
        Expense expense = approval.getExpense();
        com.expensemanagement.dto.ExpenseDto expenseDto = com.expensemanagement.dto.ExpenseDto.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .status(expense.getStatus())
                .notes(expense.getNotes())
                .receiptPath(expense.getReceiptPath())
                .receiptUrl(expense.getReceiptUrl())
                .userId(expense.getUser().getId())
                .userName(expense.getUser().getFirstName() + " " + expense.getUser().getLastName())
                .organizationId(expense.getOrganization().getId())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
        
        return ApprovalDto.builder()
                .id(approval.getId())
                .expenseId(expense.getId())
                .approverId(approval.getApprover().getId())
                .approverName(approval.getApprover().getFirstName() + " " + approval.getApprover().getLastName())
                .status(approval.getStatus())
                .comments(approval.getComments())
                .createdAt(approval.getCreatedAt())
                .expense(expenseDto)
                .build();
    }
}


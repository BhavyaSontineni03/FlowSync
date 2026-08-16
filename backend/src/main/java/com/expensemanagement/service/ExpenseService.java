package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.EventType;
import com.expensemanagement.event.ExpenseSubmittedEvent;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {
    
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final FileStorageService fileStorageService;
    private final OcrService ocrService;
    private final AsyncOcrService asyncOcrService;
    private final ActivityLogService activityLogService;
    private final WebSocketService webSocketService;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public ExpenseDto createExpense(ExpenseDto expenseDto, Long userId, Long organizationId, MultipartFile receiptFile) {
        // Validate expense amount
        if (expenseDto.getAmount() == null || expenseDto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Expense amount must be positive");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("User does not belong to this organization");
        }
        
        Expense expense = Expense.builder()
                .description(expenseDto.getDescription())
                .amount(expenseDto.getAmount())
                .expenseDate(expenseDto.getExpenseDate())
                .category(expenseDto.getCategory())
                .status(Expense.ExpenseStatus.PENDING)
                .notes(expenseDto.getNotes())
                .user(user)
                .organization(organization)
                .build();
        
        // Handle receipt upload and OCR
        if (receiptFile != null && !receiptFile.isEmpty()) {
            String receiptPath = fileStorageService.storeFile(receiptFile, organizationId, userId);
            expense.setReceiptPath(receiptPath);
            expense.setReceiptUrl("/api/expenses/receipts/" + receiptPath);
            
            // Save expense first, then process OCR asynchronously
            expense = expenseRepository.save(expense);
            
            // Process OCR asynchronously to avoid blocking the request
            try {
                asyncOcrService.processOcrAsync(expense.getId(), receiptFile, userId, organizationId);
                log.info("OCR processing started asynchronously for expense {}", expense.getId());
            } catch (Exception e) {
                log.error("Failed to start async OCR processing", e);
                // Continue without OCR - expense is already saved
            }
        } else {
            expense = expenseRepository.save(expense);
        }
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_CREATED,
                "Expense created: " + expense.getDescription() + " - $" + expense.getAmount(),
                user,
                organization,
                "Expense",
                expense.getId(),
                null
        );
        
        ExpenseDto createdDto = convertToDto(expense);
        webSocketService.sendExpenseUpdate(organizationId, createdDto);
        return createdDto;
    }

    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public ExpenseDto updateExpense(Long expenseId, ExpenseDto expenseDto, Long userId, Long organizationId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        if (!expense.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        
        // Only owner or admin can update
        if (!expense.getUser().getId().equals(userId) && 
            !isAdminOrManager(userId, organizationId)) {
            throw new RuntimeException("Not authorized to update this expense");
        }
        
        // Can't update if already approved/paid
        if (expense.getStatus() == Expense.ExpenseStatus.APPROVED || 
            expense.getStatus() == Expense.ExpenseStatus.PAID) {
            throw new RuntimeException("Cannot update approved or paid expense");
        }
        
        expense.setDescription(expenseDto.getDescription());
        expense.setAmount(expenseDto.getAmount());
        expense.setExpenseDate(expenseDto.getExpenseDate());
        expense.setCategory(expenseDto.getCategory());
        expense.setNotes(expenseDto.getNotes());
        
        expense = expenseRepository.save(expense);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_UPDATED,
                "Expense updated: " + expense.getDescription(),
                userRepository.findById(userId).orElse(null),
                expense.getOrganization(),
                "Expense",
                expense.getId(),
                null
        );
        
        return convertToDto(expense);
    }

    @Transactional
    @CacheEvict(value = "analytics", allEntries = true)
    public void deleteExpense(Long expenseId, Long userId, Long organizationId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        if (!expense.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        
        if (!expense.getUser().getId().equals(userId) && 
            !isAdminOrManager(userId, organizationId)) {
            throw new RuntimeException("Not authorized to delete this expense");
        }
        
        if (expense.getReceiptPath() != null) {
            fileStorageService.deleteFile(expense.getReceiptPath());
        }
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.EXPENSE_DELETED,
                "Expense deleted: " + expense.getDescription(),
                userRepository.findById(userId).orElse(null),
                expense.getOrganization(),
                "Expense",
                expense.getId(),
                null
        );
        
        expenseRepository.delete(expense);
    }
    
    @Transactional
    public ExpenseDto submitExpense(Long expenseId, Long userId, Long organizationId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        
        if (!expense.getUser().getId().equals(userId)) {
            throw new RuntimeException("Not authorized to submit this expense");
        }
        
        if (expense.getStatus() != Expense.ExpenseStatus.PENDING) {
            throw new RuntimeException("Expense is already submitted");
        }
        
        // Approval workflow requires an assigned manager.
        User employee = expense.getUser();
        if (employee.getManager() == null) {
            throw new RuntimeException("Cannot submit expense: You do not have a manager assigned. Please contact HR to assign a manager first.");
        }

        expense.setStatus(Expense.ExpenseStatus.SUBMITTED);
        expense.setSubmittedAt(java.time.LocalDateTime.now());
        expense = expenseRepository.save(expense);

        // Always use the expense's org, not the caller-supplied id alone.
        Long expenseOrgId = expense.getOrganization().getId();

        // Outbox event for anomaly-scoring saga (after commit).
        eventPublisher.publish(EventType.EXPENSE_SUBMITTED, "Expense", expense.getId(), expenseOrgId,
                java.util.Map.of("amount", expense.getAmount().toString(), "category", expense.getCategory().name()));

        // Defer activity log / manager notify / websocket until after commit
        // so this request does not hold a Hikari connection for side effects.
        applicationEventPublisher.publishEvent(new ExpenseSubmittedEvent(expense.getId(), expenseOrgId));

        return convertToDto(expense);
    }
    
    public Page<ExpenseDto> getExpenses(Long organizationId, Long userId, Pageable pageable) {
        return getExpenses(organizationId, userId, null, pageable);
    }
    
    public Page<ExpenseDto> getExpenses(Long organizationId, Long userId, Long managerId, Pageable pageable) {
        if (userId != null) {
            return expenseRepository.findByUserId(userId, pageable)
                    .map(this::convertToDto);
        }
        if (managerId != null) {
            return expenseRepository.findByUserManagerId(managerId, pageable)
                    .map(this::convertToDto);
        }
        return expenseRepository.findByOrganizationId(organizationId, pageable)
                .map(this::convertToDto);
    }
    
    public ExpenseDto getExpenseById(Long expenseId, Long organizationId) {
        return getExpenseById(expenseId, organizationId, null, null);
    }
    
    public ExpenseDto getExpenseById(Long expenseId, Long organizationId, Long currentUserId, String userRole) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        
        if (!expense.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        
        // Privacy check: Employees, HR, and Finance can ONLY see their own expenses
        // Managers can see expenses of their direct reports
        // Admins can see all expenses
        if (currentUserId != null && userRole != null) {
            Long expenseUserId = expense.getUser().getId();
            
            if ("EMPLOYEE".equals(userRole) || "HR".equals(userRole) || "FINANCE".equals(userRole)) {
                if (!expenseUserId.equals(currentUserId)) {
                    throw new RuntimeException("Not authorized to view this expense");
                }
            } else if ("MANAGER".equals(userRole)) {
                // Managers can view expenses of their direct reports OR their own expenses
                User expenseUser = expense.getUser();
                boolean isOwnExpense = expenseUserId.equals(currentUserId);
                boolean isDirectReport = expenseUser.getManager() != null && 
                                         expenseUser.getManager().getId().equals(currentUserId);
                
                if (!isOwnExpense && !isDirectReport) {
                    throw new RuntimeException("Not authorized to view this expense");
                }
            }
            // ADMIN can view all expenses - no check needed
        }
        
        return convertToDto(expense);
    }
    
    private ExpenseDto convertToDto(Expense expense) {
        return ExpenseDto.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .status(expense.getStatus())
                .notes(expense.getNotes())
                .receiptPath(expense.getReceiptPath())
                .receiptUrl(expense.getReceiptUrl())
                .ocrExtractedData(expense.getOcrExtractedData())
                .userId(expense.getUser().getId())
                .userName(expense.getUser().getFirstName() + " " + expense.getUser().getLastName())
                .organizationId(expense.getOrganization().getId())
                .approvals(expense.getApprovals().stream()
                        .map(a -> com.expensemanagement.dto.ApprovalDto.builder()
                                .id(a.getId())
                                .expenseId(a.getExpense().getId())
                                .approverId(a.getApprover().getId())
                                .approverName(a.getApprover().getFirstName() + " " + a.getApprover().getLastName())
                                .status(a.getStatus())
                                .comments(a.getComments())
                                .createdAt(a.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
    
    private boolean isAdminOrManager(Long userId, Long organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getRole() == User.UserRole.ADMIN || 
               user.getRole() == User.UserRole.MANAGER ||
               user.getRole() == User.UserRole.FINANCE;
    }
}


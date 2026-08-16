package com.expensemanagement.service;

import com.expensemanagement.dto.LeaveRequestDto;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Timesheet;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.LeaveRequestRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.TimesheetRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for leave request management.
 * Handles creation, approval, rejection, and leave balance tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TimesheetRepository timesheetRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final LeaveBalanceService leaveBalanceService;

    /**
     * Create a new leave request.
     * Validates leave balance and calculates working days only (excludes weekends).
     * 
     * @param leaveRequestDto Leave request data
     * @param userId User ID creating the request
     * @param organizationId Organization ID
     * @return Created leave request DTO
     */
    @Transactional
    public LeaveRequestDto createLeaveRequest(LeaveRequestDto leaveRequestDto, Long userId, Long organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("User does not belong to this organization");
        }

        // Validate dates
        if (leaveRequestDto.getStartDate().isAfter(leaveRequestDto.getEndDate())) {
            throw new RuntimeException("Start date must be before or equal to end date");
        }

        if (leaveRequestDto.getStartDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Cannot create leave request for past dates");
        }

        // Check for overlapping pending or approved leaves (prevents duplicates)
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlappingPendingOrApprovedLeaves(
                userId, leaveRequestDto.getStartDate(), leaveRequestDto.getEndDate());
        if (!overlapping.isEmpty()) {
            LeaveRequest existing = overlapping.get(0);
            String statusMsg = existing.getStatus() == LeaveRequest.LeaveStatus.PENDING ? "pending" : "approved";
            throw new RuntimeException("You already have a " + statusMsg + " leave request for this period (" + 
                    existing.getStartDate() + " to " + existing.getEndDate() + ")");
        }

        // Calculate number of WORKING days only (exclude weekends)
        int workingDays = calculateWorkingDays(leaveRequestDto.getStartDate(), leaveRequestDto.getEndDate());
        if (workingDays == 0) {
            throw new RuntimeException("Leave request must include at least one working day (Monday-Friday)");
        }
        
        // Check leave balance before creating request
        Map<String, Object> balanceCheck = leaveBalanceService.checkLeaveBalance(
                userId, leaveRequestDto.getLeaveType(), workingDays);
        
        if (!(Boolean) balanceCheck.get("sufficient")) {
            int remaining = (Integer) balanceCheck.get("remaining");
            String category = (String) balanceCheck.get("category");
            throw new RuntimeException(String.format(
                    "Insufficient %s balance. You have %d days remaining but requested %d days.",
                    category, remaining, workingDays));
        }
        
        // Determine paid/unpaid leave based on leave type
        boolean isPaid = leaveRequestDto.getLeaveType() != LeaveRequest.LeaveType.UNPAID_LEAVE;
        int paidDays = isPaid ? workingDays : 0;
        int unpaidDays = isPaid ? 0 : workingDays;

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .user(user)
                .organization(organization)
                .leaveType(leaveRequestDto.getLeaveType())
                .startDate(leaveRequestDto.getStartDate())
                .endDate(leaveRequestDto.getEndDate())
                .numberOfDays(workingDays)
                .reason(leaveRequestDto.getReason())
                .status(LeaveRequest.LeaveStatus.PENDING)
                .isPaid(isPaid)
                .paidDays(paidDays)
                .unpaidDays(unpaidDays)
                .build();

        leaveRequest = leaveRequestRepository.save(leaveRequest);

        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.LEAVE_REQUEST_CREATED,
                "Leave request created: " + leaveRequestDto.getLeaveType() + " from " + 
                leaveRequestDto.getStartDate() + " to " + leaveRequestDto.getEndDate() + 
                " (" + workingDays + " working days)",
                user,
                organization,
                "LeaveRequest",
                leaveRequest.getId(),
                null
        );

        // Notify managers
        notificationService.notifyManagersForLeaveApproval(leaveRequest);

        LeaveRequestDto createdDto = convertToDto(leaveRequest);
        createdDto.setLeaveBalance(leaveBalanceService.getBalanceSummary(userId)); // Include balance in response
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "leave_request_created");
        notification.put("leaveRequestId", leaveRequest.getId());
        webSocketService.sendNotificationToOrganization(organizationId, notification);

        return createdDto;
    }
    
    /**
     * Calculate working days between two dates (excluding weekends).
     */
    private int calculateWorkingDays(LocalDate start, LocalDate end) {
        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    /**
     * Approve a leave request.
     * When approved:
     * 1. Updates leave balance (deducts used days)
     * 2. Auto-creates timesheet entries for each leave day (Mon-Fri only)
     * 3. These timesheet entries are marked as LEAVE and cannot be edited by user
     * 
     * @param leaveRequestId Leave request ID
     * @param approverId Approver user ID
     * @param comments Approval comments
     * @return Approved leave request DTO
     */
    @Transactional
    public LeaveRequestDto approveLeaveRequest(Long leaveRequestId, Long approverId, String comments) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        User employee = leaveRequest.getUser();
        
        // Prevent self-approval - user cannot approve their own leave request
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot approve your own leave request.");
        }

        // ONLY the employee's assigned manager can approve leave requests
        // This ensures the decision is made by the person who knows the employee's workload
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can approve this leave request.");
        }

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Leave request is not pending");
        }

        leaveRequest.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        leaveRequest.setApprovedBy(approver);
        leaveRequest.setApprovalComments(comments);
        leaveRequest.setApprovedAt(java.time.LocalDateTime.now());
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        // 1. Deduct from leave balance
        leaveBalanceService.deductLeave(
                leaveRequest.getUser().getId(),
                leaveRequest.getLeaveType(),
                leaveRequest.getNumberOfDays()
        );

        // 2. Auto-create timesheet entries for each leave day (working days only)
        createTimesheetEntriesForLeave(leaveRequest);

        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.LEAVE_REQUEST_APPROVED,
                "Leave request approved: " + leaveRequest.getLeaveType() + " by " + 
                approver.getFirstName() + " " + approver.getLastName() + 
                ". Timesheet entries auto-generated for " + leaveRequest.getNumberOfDays() + " days.",
                approver,
                leaveRequest.getOrganization(),
                "LeaveRequest",
                leaveRequest.getId(),
                comments
        );

        notificationService.notifyLeaveApproved(leaveRequest, approver);

        LeaveRequestDto approvedDto = convertToDto(leaveRequest);
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "leave_request_approved");
        notification.put("leaveRequestId", leaveRequest.getId());
        webSocketService.sendNotificationToOrganization(leaveRequest.getOrganization().getId(), notification);

        return approvedDto;
    }

    /**
     * Auto-create timesheet entries for approved leave.
     * Creates LEAVE type entries for each working day in the leave period.
     * These entries are pre-approved and cannot be edited by users.
     */
    private void createTimesheetEntriesForLeave(LeaveRequest leaveRequest) {
        User user = leaveRequest.getUser();
        Organization organization = leaveRequest.getOrganization();
        LocalDate current = leaveRequest.getStartDate();
        
        while (!current.isAfter(leaveRequest.getEndDate())) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            
            // Only create entries for working days (Mon-Fri)
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                // Check if a timesheet entry already exists for this date
                var existingEntry = timesheetRepository.findByUserIdAndDate(user.getId(), current);
                
                if (existingEntry.isPresent()) {
                    // If existing entry is a DRAFT work entry, replace it with leave entry
                    Timesheet existing = existingEntry.get();
                    Timesheet.EntryType entryType = existing.getEntryType();
                    
                    // Handle null entryType (legacy data) - treat as WORK
                    if (entryType == null) {
                        entryType = Timesheet.EntryType.WORK;
                    }
                    
                    if (entryType == Timesheet.EntryType.WORK && 
                        existing.getStatus() == Timesheet.TimesheetStatus.DRAFT) {
                        // Delete the draft entry and create leave entry
                        timesheetRepository.delete(existing);
                        timesheetRepository.flush(); // Ensure delete is committed
                    } else if (entryType == Timesheet.EntryType.LEAVE) {
                        // Leave entry already exists, skip
                        current = current.plusDays(1);
                        continue;
                    } else {
                        // Submitted/approved work entry exists - this shouldn't happen
                        // but log and skip to avoid data loss
                        log.warn("Existing submitted/approved timesheet for date {} while processing leave. Skipping.", current);
                        current = current.plusDays(1);
                        continue;
                    }
                }
                
                // Create leave timesheet entry
                Timesheet leaveEntry = Timesheet.builder()
                        .user(user)
                        .organization(organization)
                        .project(null) // No project for leave
                        .projectCode("LEAVE")
                        .date(current)
                        .hours(0.0) // 0 hours worked (on leave)
                        .description(leaveRequest.getLeaveType().name() + " - " + 
                                (leaveRequest.getReason() != null ? leaveRequest.getReason() : "Leave"))
                        .status(Timesheet.TimesheetStatus.APPROVED) // Auto-approved
                        .entryType(Timesheet.EntryType.LEAVE)
                        .leaveRequest(leaveRequest)
                        .leaveType(leaveRequest.getLeaveType())
                        .isPaidLeave(leaveRequest.getIsPaid())
                        .approvedBy(leaveRequest.getApprovedBy())
                        .approvedAt(java.time.LocalDateTime.now())
                        .submittedAt(java.time.LocalDateTime.now())
                        .build();
                
                timesheetRepository.save(leaveEntry);
                log.info("Created leave timesheet entry for user {} on date {}", user.getId(), current);
            }
            
            current = current.plusDays(1);
        }
    }

    /**
     * Reject a leave request.
     * 
     * @param leaveRequestId Leave request ID
     * @param approverId Approver user ID
     * @param comments Rejection comments (required)
     * @return Rejected leave request DTO
     */
    @Transactional
    public LeaveRequestDto rejectLeaveRequest(Long leaveRequestId, Long approverId, String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new RuntimeException("Rejection comments are required");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        User employee = leaveRequest.getUser();
        
        // Prevent self-rejection
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot reject your own leave request.");
        }

        // ONLY the employee's assigned manager can reject leave requests
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can reject this leave request.");
        }

        if (leaveRequest.getStatus() != LeaveRequest.LeaveStatus.PENDING) {
            throw new RuntimeException("Leave request is not pending");
        }

        leaveRequest.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        leaveRequest.setApprovedBy(approver);
        leaveRequest.setApprovalComments(comments);
        leaveRequest.setApprovedAt(java.time.LocalDateTime.now());
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.LEAVE_REQUEST_REJECTED,
                "Leave request rejected: " + leaveRequest.getLeaveType() + " by " + 
                approver.getFirstName() + " " + approver.getLastName(),
                approver,
                leaveRequest.getOrganization(),
                "LeaveRequest",
                leaveRequest.getId(),
                comments
        );

        notificationService.notifyLeaveRejected(leaveRequest, approver);

        LeaveRequestDto rejectedDto = convertToDto(leaveRequest);
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "leave_request_rejected");
        notification.put("leaveRequestId", leaveRequest.getId());
        webSocketService.sendNotificationToOrganization(leaveRequest.getOrganization().getId(), notification);

        return rejectedDto;
    }

    /**
     * Get leave requests for an organization or user.
     * 
     * @param organizationId Organization ID
     * @param userId Optional user ID (if null, returns all organization leaves)
     * @param pageable Pagination
     * @return Page of leave request DTOs
     */
    public Page<LeaveRequestDto> getLeaveRequests(Long organizationId, Long userId, Pageable pageable) {
        return getLeaveRequests(organizationId, userId, null, pageable);
    }
    
    public Page<LeaveRequestDto> getLeaveRequests(Long organizationId, Long userId, Long managerId, Pageable pageable) {
        if (userId != null) {
            return leaveRequestRepository.findByUserId(userId, pageable)
                    .map(this::convertToDto);
        }
        if (managerId != null) {
            return leaveRequestRepository.findByUserManagerId(managerId, pageable)
                    .map(this::convertToDto);
        }
        return leaveRequestRepository.findByOrganizationId(organizationId, pageable)
                .map(this::convertToDto);
    }

    /**
     * Get pending leave requests for approval (legacy - returns all pending).
     * 
     * @param approverId Approver user ID
     * @return List of pending leave request DTOs
     */
    public List<LeaveRequestDto> getPendingLeaveRequests(Long approverId) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return leaveRequestRepository.findPendingByDateRange(
                approver.getOrganization().getId(),
                LeaveRequest.LeaveStatus.PENDING,
                null,
                null
        ).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get pending leave requests for a specific manager.
     * Only returns requests from employees where the given user is their assigned manager.
     * This ensures managers only see and can approve requests from their direct reports.
     * 
     * @param managerId Manager user ID
     * @return List of pending leave request DTOs from their direct reports
     */
    public List<LeaveRequestDto> getPendingLeaveRequestsForManager(Long managerId) {
        return leaveRequestRepository.findByUserManagerIdAndStatusOrderByCreatedAtDesc(
                managerId, LeaveRequest.LeaveStatus.PENDING).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get leave balance for a user.
     * Uses the new LeaveBalanceService for accurate tracking.
     * 
     * @param userId User ID
     * @param leaveType Leave type (optional, null returns all)
     * @param year Year
     * @return Map with balance information
     */
    public Map<String, Object> getLeaveBalance(Long userId, LeaveRequest.LeaveType leaveType, int year) {
        if (leaveType != null) {
            return leaveBalanceService.checkLeaveBalance(userId, leaveType, 0);
        }
        return leaveBalanceService.getBalanceSummary(userId);
    }

    /**
     * Get complete leave balance summary for a user.
     */
    public Map<String, Object> getLeaveBalanceSummary(Long userId) {
        return leaveBalanceService.getBalanceSummary(userId);
    }

    private LeaveRequestDto convertToDto(LeaveRequest leaveRequest) {
        return LeaveRequestDto.builder()
                .id(leaveRequest.getId())
                .userId(leaveRequest.getUser().getId())
                .isPaid(leaveRequest.getIsPaid())
                .paidDays(leaveRequest.getPaidDays())
                .unpaidDays(leaveRequest.getUnpaidDays())
                .userName(leaveRequest.getUser().getFirstName() + " " + leaveRequest.getUser().getLastName())
                .organizationId(leaveRequest.getOrganization().getId())
                .leaveType(leaveRequest.getLeaveType())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .numberOfDays(leaveRequest.getNumberOfDays())
                .reason(leaveRequest.getReason())
                .status(leaveRequest.getStatus())
                .approvedById(leaveRequest.getApprovedBy() != null ? leaveRequest.getApprovedBy().getId() : null)
                .approvedByName(leaveRequest.getApprovedBy() != null ? 
                        leaveRequest.getApprovedBy().getFirstName() + " " + leaveRequest.getApprovedBy().getLastName() : null)
                .approvalComments(leaveRequest.getApprovalComments())
                .createdAt(leaveRequest.getCreatedAt())
                .updatedAt(leaveRequest.getUpdatedAt())
                .approvedAt(leaveRequest.getApprovedAt())
                .build();
    }
}


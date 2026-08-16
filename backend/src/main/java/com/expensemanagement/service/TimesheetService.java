package com.expensemanagement.service;

import com.expensemanagement.dto.TimesheetDto;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.Project;
import com.expensemanagement.model.Timesheet;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.ProjectAssignmentRepository;
import com.expensemanagement.repository.ProjectRepository;
import com.expensemanagement.repository.TimesheetRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for timesheet management.
 * Handles timesheet submission, approval, and project code validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetService {
    
    private final TimesheetRepository timesheetRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    
    private static final String BENCH_CODE = "BENCH";
    
    @Transactional
    public TimesheetDto createTimesheet(TimesheetDto timesheetDto, Long userId, Long organizationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("User does not belong to this organization");
        }
        
        // Check if it's a weekend
        java.time.DayOfWeek dayOfWeek = timesheetDto.getDate().getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            throw new RuntimeException("Cannot create timesheet for weekends. Timesheets are for working days (Monday-Friday) only.");
        }
        
        // Check if timesheet already exists for this date
        var existingEntry = timesheetRepository.findByUserIdAndDate(userId, timesheetDto.getDate());
        if (existingEntry.isPresent()) {
            Timesheet existing = existingEntry.get();
            // Check if it's a leave entry - cannot be modified
            if (existing.getEntryType() == Timesheet.EntryType.LEAVE) {
                throw new RuntimeException("You have an approved leave on " + timesheetDto.getDate() + 
                        " (" + existing.getLeaveType() + "). Cannot create timesheet for leave days.");
            }
            throw new RuntimeException("Timesheet already exists for this date");
        }
        
        // Validate project code
        Project project = null;
        if (timesheetDto.getProjectCode() != null && !timesheetDto.getProjectCode().equals(BENCH_CODE)) {
            project = projectRepository.findByCodeAndOrganizationId(timesheetDto.getProjectCode(), organizationId)
                    .orElseThrow(() -> new RuntimeException("Project not found with code: " + timesheetDto.getProjectCode()));
            
            // Check if user is assigned to this project
            if (projectAssignmentRepository.findByUserIdAndProjectIdAndIsActiveTrue(userId, project.getId()).isEmpty()) {
                throw new RuntimeException("You are not assigned to project: " + timesheetDto.getProjectCode());
            }
        } else if (timesheetDto.getProjectCode() == null || timesheetDto.getProjectCode().equals(BENCH_CODE)) {
            // Bench code - user must be on bench
            if (!user.getIsOnBench()) {
                throw new RuntimeException("You are assigned to projects. Please use your project code instead of BENCH.");
            }
        }
        
        Timesheet timesheet = Timesheet.builder()
                .user(user)
                .project(project)
                .projectCode(timesheetDto.getProjectCode() != null ? timesheetDto.getProjectCode() : BENCH_CODE)
                .date(timesheetDto.getDate())
                .hours(timesheetDto.getHours())
                .description(timesheetDto.getDescription())
                .status(Timesheet.TimesheetStatus.DRAFT)
                .organization(organization)
                .entryType(Timesheet.EntryType.WORK) // Explicitly set as WORK entry
                .build();
        
        timesheet = timesheetRepository.save(timesheet);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.TIMESHEET_CREATED,
                "Timesheet created for " + timesheetDto.getDate() + " - " + timesheetDto.getProjectCode(),
                user,
                organization,
                "Timesheet",
                timesheet.getId(),
                null
        );
        
        return convertToDto(timesheet);
    }
    
    /**
     * Update an existing timesheet.
     * Only the owner can update their own timesheet, and only if it's in DRAFT status.
     */
    @Transactional
    public TimesheetDto updateTimesheet(Long timesheetId, TimesheetDto timesheetDto, Long userId, Long organizationId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Timesheet not found"));
        
        // Only owner can update their own timesheet
        if (!timesheet.getUser().getId().equals(userId)) {
            throw new RuntimeException("You are not authorized to modify this timesheet. You can only modify your own timesheets.");
        }
        
        // Only DRAFT status can be updated
        if (timesheet.getStatus() != Timesheet.TimesheetStatus.DRAFT) {
            throw new RuntimeException("Cannot modify timesheet. Only DRAFT timesheets can be modified.");
        }
        
        // Cannot update LEAVE entries
        if (timesheet.getEntryType() == Timesheet.EntryType.LEAVE) {
            throw new RuntimeException("Cannot modify leave entries. Leave entries are auto-generated from approved leave requests.");
        }
        
        // Update fields
        if (timesheetDto.getHours() != null) {
            timesheet.setHours(timesheetDto.getHours());
        }
        if (timesheetDto.getDescription() != null) {
            timesheet.setDescription(timesheetDto.getDescription());
        }
        
        // Update project code if changed
        if (timesheetDto.getProjectCode() != null && !timesheetDto.getProjectCode().equals(timesheet.getProjectCode())) {
            if (!timesheetDto.getProjectCode().equals(BENCH_CODE)) {
                Project project = projectRepository.findByCodeAndOrganizationId(timesheetDto.getProjectCode(), organizationId)
                        .orElseThrow(() -> new RuntimeException("Project not found with code: " + timesheetDto.getProjectCode()));
                
                // Check if user is assigned to this project
                if (projectAssignmentRepository.findByUserIdAndProjectIdAndIsActiveTrue(userId, project.getId()).isEmpty()) {
                    throw new RuntimeException("You are not assigned to project: " + timesheetDto.getProjectCode());
                }
                timesheet.setProject(project);
            } else {
                timesheet.setProject(null);
            }
            timesheet.setProjectCode(timesheetDto.getProjectCode());
        }
        
        timesheet = timesheetRepository.save(timesheet);
        
        activityLogService.logActivity(
                com.expensemanagement.model.ActivityLog.ActivityType.TIMESHEET_CREATED,
                "Timesheet updated for " + timesheet.getDate(),
                timesheet.getUser(),
                timesheet.getOrganization(),
                "Timesheet",
                timesheet.getId(),
                null
        );
        
        return convertToDto(timesheet);
    }
    
    @Transactional
    public TimesheetDto submitTimesheet(Long timesheetId, Long userId, Long organizationId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Timesheet not found"));
        
        if (!timesheet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Not authorized to submit this timesheet");
        }
        
        if (timesheet.getStatus() != Timesheet.TimesheetStatus.DRAFT) {
            throw new RuntimeException("Timesheet is already submitted");
        }
        
        timesheet.setStatus(Timesheet.TimesheetStatus.SUBMITTED);
        timesheet.setSubmittedAt(java.time.LocalDateTime.now());
        timesheet = timesheetRepository.save(timesheet);
        
        // Notify manager
        if (timesheet.getUser().getManager() != null) {
            notificationService.notifyManagersForTimesheetApproval(timesheet);
        }
        
        return convertToDto(timesheet);
    }
    
    @Transactional
    public TimesheetDto approveTimesheet(Long timesheetId, Long approverId, String comments) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Timesheet not found"));
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        
        User employee = timesheet.getUser();
        
        // Prevent self-approval
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot approve your own timesheet.");
        }
        
        // ONLY the employee's assigned manager can approve timesheets
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can approve this timesheet.");
        }
        
        if (timesheet.getStatus() != Timesheet.TimesheetStatus.SUBMITTED) {
            throw new RuntimeException("Timesheet is not submitted");
        }
        
        timesheet.setStatus(Timesheet.TimesheetStatus.APPROVED);
        timesheet.setApprovedBy(approver);
        timesheet.setApprovalComments(comments);
        timesheet.setApprovedAt(java.time.LocalDateTime.now());
        timesheet = timesheetRepository.save(timesheet);
        
        return convertToDto(timesheet);
    }
    
    @Transactional
    public TimesheetDto rejectTimesheet(Long timesheetId, Long approverId, String comments) {
        if (comments == null || comments.trim().isEmpty()) {
            throw new RuntimeException("Rejection comments are required");
        }
        
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Timesheet not found"));
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        
        User employee = timesheet.getUser();
        
        // Prevent self-rejection
        if (employee.getId().equals(approverId)) {
            throw new RuntimeException("You cannot reject your own timesheet.");
        }
        
        // ONLY the employee's assigned manager can reject timesheets
        User employeeManager = employee.getManager();
        if (employeeManager == null) {
            throw new RuntimeException("This employee does not have an assigned manager. Please contact HR to assign a manager first.");
        }
        
        if (!employeeManager.getId().equals(approverId)) {
            throw new RuntimeException("Only " + employeeManager.getFirstName() + " " + employeeManager.getLastName() + 
                    " (the employee's assigned manager) can reject this timesheet.");
        }
        
        if (timesheet.getStatus() != Timesheet.TimesheetStatus.SUBMITTED) {
            throw new RuntimeException("Timesheet is not submitted");
        }
        
        timesheet.setStatus(Timesheet.TimesheetStatus.REJECTED);
        timesheet.setApprovedBy(approver);
        timesheet.setApprovalComments(comments);
        timesheet.setApprovedAt(java.time.LocalDateTime.now());
        timesheet = timesheetRepository.save(timesheet);
        
        return convertToDto(timesheet);
    }
    
    public Page<TimesheetDto> getTimesheets(Long organizationId, Long userId, Pageable pageable) {
        return getTimesheets(organizationId, userId, null, pageable);
    }
    
    public Page<TimesheetDto> getTimesheets(Long organizationId, Long userId, Long managerId, Pageable pageable) {
        if (userId != null) {
            return timesheetRepository.findByUserId(userId, pageable)
                    .map(this::convertToDto);
        }
        if (managerId != null) {
            return timesheetRepository.findByUserManagerId(managerId, pageable)
                    .map(this::convertToDto);
        }
        return timesheetRepository.findByOrganizationId(organizationId, pageable)
                .map(this::convertToDto);
    }
    
    public List<TimesheetDto> getPendingTimesheets(Long approverId) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        
        return timesheetRepository.findPendingByOrganization(
                approver.getOrganization().getId(),
                Timesheet.TimesheetStatus.SUBMITTED
        ).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get submitted timesheets for a specific manager.
     * Only returns timesheets from employees where the given user is their assigned manager.
     * This ensures managers only see and can approve timesheets from their direct reports.
     * 
     * @param managerId Manager user ID
     * @return List of submitted timesheet DTOs from their direct reports
     */
    public List<TimesheetDto> getPendingTimesheetsForManager(Long managerId) {
        return timesheetRepository.findByUserManagerIdAndStatusOrderByDateDesc(
                managerId, Timesheet.TimesheetStatus.SUBMITTED).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public Integer getDaysWorked(Long userId, LocalDate startDate, LocalDate endDate) {
        return timesheetRepository.countApprovedDaysInRange(userId, startDate, endDate);
    }
    
    /**
     * Get weekly timesheet entries for a user.
     * Returns entries for Mon-Fri of the specified week, including:
     * - Existing work entries
     * - Existing leave entries (auto-generated)
     * - Placeholder info for unfilled days
     */
    public List<TimesheetDto> getWeeklyTimesheets(Long userId, LocalDate weekStartDate) {
        // Ensure we start from Monday
        LocalDate monday = weekStartDate.with(java.time.DayOfWeek.MONDAY);
        LocalDate friday = monday.plusDays(4);
        
        List<Timesheet> entries = timesheetRepository.findByUserIdAndDateRangeOrdered(userId, monday, friday);
        
        return entries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get weekly timesheet summary with status for each day.
     */
    public java.util.Map<String, Object> getWeeklySummary(Long userId, LocalDate weekStartDate) {
        LocalDate monday = weekStartDate.with(java.time.DayOfWeek.MONDAY);
        
        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("weekStartDate", monday);
        summary.put("weekEndDate", monday.plusDays(4));
        
        java.util.List<java.util.Map<String, Object>> days = new java.util.ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            LocalDate date = monday.plusDays(i);
            java.util.Map<String, Object> dayInfo = new java.util.HashMap<>();
            dayInfo.put("date", date);
            dayInfo.put("dayOfWeek", date.getDayOfWeek().toString());
            
            var entry = timesheetRepository.findByUserIdAndDate(userId, date);
            if (entry.isPresent()) {
                Timesheet ts = entry.get();
                dayInfo.put("status", ts.getStatus().name());
                dayInfo.put("entryType", ts.getEntryType().name());
                dayInfo.put("projectCode", ts.getProjectCode());
                dayInfo.put("hours", ts.getHours());
                dayInfo.put("isEditable", ts.isEditable());
                dayInfo.put("timesheetId", ts.getId());
                if (ts.getEntryType() == Timesheet.EntryType.LEAVE) {
                    dayInfo.put("leaveType", ts.getLeaveType() != null ? ts.getLeaveType().name() : null);
                    dayInfo.put("isPaidLeave", ts.getIsPaidLeave());
                }
            } else {
                dayInfo.put("status", "EMPTY");
                dayInfo.put("entryType", null);
                dayInfo.put("isEditable", true);
            }
            
            days.add(dayInfo);
        }
        
        summary.put("days", days);
        return summary;
    }
    
    private TimesheetDto convertToDto(Timesheet timesheet) {
        String displayName;
        if (timesheet.getEntryType() == Timesheet.EntryType.LEAVE) {
            displayName = "Leave - " + (timesheet.getLeaveType() != null ? timesheet.getLeaveType().name() : "");
        } else if (timesheet.getProject() != null) {
            displayName = timesheet.getProject().getName();
        } else {
            displayName = "Bench";
        }
        
        return TimesheetDto.builder()
                .id(timesheet.getId())
                .userId(timesheet.getUser().getId())
                .userName(timesheet.getUser().getFirstName() + " " + timesheet.getUser().getLastName())
                .projectId(timesheet.getProject() != null ? timesheet.getProject().getId() : null)
                .projectCode(timesheet.getProjectCode())
                .projectName(displayName)
                .date(timesheet.getDate())
                .hours(timesheet.getHours())
                .description(timesheet.getDescription())
                .status(timesheet.getStatus().name())
                .organizationId(timesheet.getOrganization().getId())
                .approvedBy(timesheet.getApprovedBy() != null ? timesheet.getApprovedBy().getId() : null)
                .approvedByName(timesheet.getApprovedBy() != null ? 
                        timesheet.getApprovedBy().getFirstName() + " " + timesheet.getApprovedBy().getLastName() : null)
                .approvalComments(timesheet.getApprovalComments())
                .submittedAt(timesheet.getSubmittedAt())
                .approvedAt(timesheet.getApprovedAt())
                .createdAt(timesheet.getCreatedAt())
                .updatedAt(timesheet.getUpdatedAt())
                // New fields for leave integration
                .entryType(timesheet.getEntryType() != null ? timesheet.getEntryType().name() : "WORK")
                .leaveType(timesheet.getLeaveType() != null ? timesheet.getLeaveType().name() : null)
                .isPaidLeave(timesheet.getIsPaidLeave())
                .isEditable(timesheet.isEditable())
                .leaveRequestId(timesheet.getLeaveRequest() != null ? timesheet.getLeaveRequest().getId() : null)
                .build();
    }
}


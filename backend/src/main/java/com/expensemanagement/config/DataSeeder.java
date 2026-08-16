package com.expensemanagement.config;

import com.expensemanagement.model.*;
import com.expensemanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Data seeder for development and testing.
 * Creates sample organizations, users, projects, assignments, timesheets, payroll, expenses, leave requests, and activity logs.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final TimesheetRepository timesheetRepository;
    private final PayrollRepository payrollRepository;
    private final ExpenseRepository expenseRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ActivityLogRepository activityLogRepository;
    private final com.expensemanagement.repository.OrgBudgetRepository orgBudgetRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Store users for reference
    private Organization organization;
    private User admin;
    private User manager1;
    private User hr1;
    private User finance1;
    private List<User> employees = new ArrayList<>();
    private List<Project> projects = new ArrayList<>();
    
    @Override
    @Transactional
    public void run(String... args) {
        if (organizationRepository.count() > 0) {
            log.info("Data already exists. Skipping seed data creation.");
            return;
        }
        
        log.info("Starting comprehensive data seeding...");
        
        createOrganization();
        createUsers();
        createLeaveBalances(); // Create leave balances after users
        createProjects();
        createProjectAssignments();
        createTimesheets();
        createPayroll();
        createExpenses();
        createLeaveRequests();
        createActivityLogs();
        seedOrgBudget(organization);
        seedPartnerOrganizations();
        
        log.info("Data seeding completed successfully!");
        logSummary();
    }

    /**
     * The current month's spending envelope for the main demo org. The
     * payment saga step reads this to decide whether an approved expense
     * can actually be paid -- see PaymentSagaStep.
     */
    private void seedOrgBudget(Organization org) {
        LocalDate now = LocalDate.now();
        orgBudgetRepository.save(OrgBudget.builder()
                .organization(org)
                .periodYear(now.getYear())
                .periodMonth(now.getMonthValue())
                .allocatedAmount(new BigDecimal("50000.00"))
                .consumedAmount(BigDecimal.ZERO)
                .build());
    }

    /**
     * Four additional lightweight organizations purely to demonstrate that
     * Kafka event isolation is real per-tenant separation and not just
     * config for a single org: each gets its own topic
     * (expense-events.org-{id}), its own budget envelope, and enough users
     * to run the submit -> score -> approve -> pay saga end to end. These
     * orgs intentionally skip the full project/timesheet/payroll history the
     * main demo org has -- that richness isn't needed to prove isolation.
     */
    private void seedPartnerOrganizations() {
        String[][] partners = {
                {"Meridian Logistics", "meridian", "contact@meridianlogistics.com"},
                {"Harborlight Design Studio", "harborlight", "hello@harborlightdesign.com"},
                {"Ferngrove Analytics", "ferngrove", "info@ferngroveanalytics.com"},
                {"Cobalt Peak Consulting", "cobaltpeak", "team@cobaltpeakconsulting.com"},
        };

        for (String[] partner : partners) {
            Organization org = organizationRepository.save(Organization.builder()
                    .name(partner[0])
                    .subdomain(partner[1])
                    .address("Address on file")
                    .contactEmail(partner[2])
                    .contactPhone("+1-555-000-0000")
                    .build());

            User orgManager = userRepository.save(User.builder()
                    .firstName("Morgan")
                    .lastName("Reyes")
                    .email("manager@" + partner[1] + ".com")
                    .password(passwordEncoder.encode("partner123"))
                    .role(User.UserRole.MANAGER)
                    .organization(org)
                    .enabled(true)
                    .build());

            User orgEmployee = userRepository.save(User.builder()
                    .firstName("Casey")
                    .lastName("Nguyen")
                    .email("employee@" + partner[1] + ".com")
                    .password(passwordEncoder.encode("partner123"))
                    .role(User.UserRole.EMPLOYEE)
                    .organization(org)
                    .manager(orgManager)
                    .enabled(true)
                    .build());

            LocalDate now = LocalDate.now();
            orgBudgetRepository.save(OrgBudget.builder()
                    .organization(org)
                    .periodYear(now.getYear())
                    .periodMonth(now.getMonthValue())
                    .allocatedAmount(new BigDecimal("12000.00"))
                    .consumedAmount(BigDecimal.ZERO)
                    .build());

            log.info("Seeded partner organization '{}' (subdomain: {}, manager: {}, employee: {})",
                    org.getName(), org.getSubdomain(), orgManager.getEmail(), orgEmployee.getEmail());
        }
    }
    
    private void createOrganization() {
        organization = Organization.builder()
                .name("TechCorp Solutions")
                .subdomain("techcorp")
                .address("123 Tech Street, Silicon Valley, CA 94000")
                .contactEmail("contact@techcorp.com")
                .contactPhone("+1-555-123-4567")
                .build();
        organization = organizationRepository.save(organization);
        log.info("Created organization: {}", organization.getName());
    }
    
    private void createUsers() {
        // Create Admin
        admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@techcorp.com")
                .password(passwordEncoder.encode("admin123"))
                .role(User.UserRole.ADMIN)
                .organization(organization)
                .enabled(true)
                .isOnBench(false)
                .monthlySalary(new BigDecimal("100000.00"))
                .build();
        admin = userRepository.save(admin);
        log.info("Created admin: {} (password: admin123)", admin.getEmail());
        
        // Create Manager1
        manager1 = User.builder()
                .firstName("Manager")
                .lastName("One")
                .email("manager1@techcorp.com")
                .password(passwordEncoder.encode("manager123"))
                .role(User.UserRole.MANAGER)
                .organization(organization)
                .enabled(true)
                .isOnBench(false)
                .monthlySalary(new BigDecimal("80000.00"))
                .build();
        manager1 = userRepository.save(manager1);
        log.info("Created manager: {} (password: manager123)", manager1.getEmail());
        
        // Create HR1
        hr1 = User.builder()
                .firstName("HR")
                .lastName("One")
                .email("hr1@techcorp.com")
                .password(passwordEncoder.encode("hr123"))
                .role(User.UserRole.HR)
                .organization(organization)
                .enabled(true)
                .isOnBench(false)
                .monthlySalary(new BigDecimal("75000.00"))
                .build();
        hr1 = userRepository.save(hr1);
        log.info("Created HR: {} (password: hr123)", hr1.getEmail());
        
        // Create Finance1
        finance1 = User.builder()
                .firstName("Finance")
                .lastName("One")
                .email("finance1@techcorp.com")
                .password(passwordEncoder.encode("finance123"))
                .role(User.UserRole.FINANCE)
                .organization(organization)
                .enabled(true)
                .isOnBench(false)
                .monthlySalary(new BigDecimal("70000.00"))
                .build();
        finance1 = userRepository.save(finance1);
        log.info("Created finance: {} (password: finance123)", finance1.getEmail());
        
        // Create 5 Employees with different scenarios
        // Employee1: On project1 (first project, active)
        // Employee2: On project1 (first project, active)
        // Employee3: On project1 (second project) - finished project2
        // Employee4: Finished a project, now on bench
        // Employee5: Never had a project, on bench
        
        String[] employeeScenarios = {
            "Active on first project",
            "Active on first project", 
            "Active on second project (finished one)",
            "Finished project, now on bench",
            "Never assigned, on bench"
        };
        
        for (int i = 1; i <= 5; i++) {
            boolean isOnBench = (i >= 4); // employee4 and employee5 are on bench
            
            User employee = User.builder()
                    .firstName("Employee")
                    .lastName(String.valueOf(i))
                    .email("employee" + i + "@techcorp.com")
                    .password(passwordEncoder.encode("employee123"))
                    .role(User.UserRole.EMPLOYEE)
                    .organization(organization)
                    .enabled(true)
                    .isOnBench(isOnBench)
                    .manager(manager1)
                    .hr(hr1)
                    .monthlySalary(new BigDecimal("50000.00")) // All employees get 50k
                    .build();
            employee = userRepository.save(employee);
            employees.add(employee);
            // All seeded employees share the same password ("employee123") --
            // only the email varies. The old log line here implied a distinct
            // per-user password ("employee1", "employee2"...), which does not
            // match what passwordEncoder.encode(...) above actually hashes.
            log.info("Created employee: {} (password: employee123) - {}", employee.getEmail(), employeeScenarios[i-1]);
        }
    }
    
    /**
     * Create leave balances for all users for the current year.
     * Industry standard allocations:
     * - Paid Leave: 20 days
     * - Unpaid Leave: 12 days (fixed as per Indian IT industry)
     * - Sick Leave: 10 days
     * - Personal Leave: 5 days
     */
    private void createLeaveBalances() {
        int currentYear = LocalDate.now().getYear();
        
        // Create leave balances for all users
        List<User> allUsers = new ArrayList<>();
        allUsers.add(admin);
        allUsers.add(manager1);
        allUsers.add(hr1);
        allUsers.add(finance1);
        allUsers.addAll(employees);
        
        for (User user : allUsers) {
            // Simulate some usage for employees to make data realistic
            int paidUsed = 0;
            int unpaidUsed = 0;
            int sickUsed = 0;
            int personalUsed = 0;
            
            if (user.getRole() == User.UserRole.EMPLOYEE) {
                // Employees have used some leaves already this year
                Random random = new Random(user.getId()); // Deterministic based on user ID
                paidUsed = random.nextInt(5); // 0-4 paid leaves used
                sickUsed = random.nextInt(3); // 0-2 sick leaves used
                personalUsed = random.nextInt(2); // 0-1 personal leaves used
            }
            
            LeaveBalance balance = LeaveBalance.builder()
                    .user(user)
                    .organization(organization)
                    .year(currentYear)
                    .paidLeaveAllocated(20)
                    .paidLeaveUsed(paidUsed)
                    .unpaidLeaveAllocated(12) // Fixed 12 unpaid leaves
                    .unpaidLeaveUsed(unpaidUsed)
                    .sickLeaveAllocated(10)
                    .sickLeaveUsed(sickUsed)
                    .personalLeaveAllocated(5)
                    .personalLeaveUsed(personalUsed)
                    .build();
            
            leaveBalanceRepository.save(balance);
        }
        
        log.info("Created leave balances for {} users for year {}", allUsers.size(), currentYear);
    }
    
    private void createProjects() {
        LocalDate today = LocalDate.now();
        
        // Project1: Active - 3 employees working on it - Managed by manager1
        Project project1 = Project.builder()
                .code("PROJ001")
                .name("E-Commerce Platform")
                .description("Building a modern e-commerce platform with React and Spring Boot")
                .startDate(today.minusMonths(3))
                .endDate(today.plusMonths(6))
                .status(Project.ProjectStatus.ACTIVE)
                .organization(organization)
                .manager(manager1)
                .build();
        project1 = projectRepository.save(project1);
        projects.add(project1);
        log.info("Created project {} managed by {}", project1.getCode(), manager1.getEmail());
        
        // Project2: Completed - Employee3 and Employee4's first project - Managed by manager1
        Project project2 = Project.builder()
                .code("PROJ002")
                .name("Mobile App Development")
                .description("Cross-platform mobile app development using React Native")
                .startDate(today.minusMonths(8))
                .endDate(today.minusMonths(2))
                .status(Project.ProjectStatus.COMPLETED)
                .organization(organization)
                .manager(manager1)
                .build();
        project2 = projectRepository.save(project2);
        projects.add(project2);
        
        // Project3: Active - Managed by admin
        Project project3 = Project.builder()
                .code("PROJ003")
                .name("Cloud Migration")
                .description("Migrating legacy systems to AWS cloud infrastructure")
                .startDate(today.minusMonths(1))
                .endDate(today.plusMonths(8))
                .status(Project.ProjectStatus.ACTIVE)
                .organization(organization)
                .manager(admin)
                .build();
        project3 = projectRepository.save(project3);
        projects.add(project3);
        
        // Project4: Active - Managed by manager1
        Project project4 = Project.builder()
                .code("PROJ004")
                .name("Data Analytics Dashboard")
                .description("Building a real-time analytics dashboard for business intelligence")
                .startDate(today.minusMonths(2))
                .endDate(today.plusMonths(4))
                .status(Project.ProjectStatus.ACTIVE)
                .organization(organization)
                .manager(manager1)
                .build();
        project4 = projectRepository.save(project4);
        projects.add(project4);
        
        // Project5: On Hold - Managed by admin
        Project project5 = Project.builder()
                .code("PROJ005")
                .name("AI Chatbot Integration")
                .description("Integrating AI-powered chatbot for customer support")
                .startDate(today.plusMonths(1))
                .endDate(today.plusMonths(5))
                .status(Project.ProjectStatus.ON_HOLD)
                .organization(organization)
                .manager(admin)
                .build();
        project5 = projectRepository.save(project5);
        projects.add(project5);
        
        log.info("Created {} projects with managers assigned", projects.size());
    }
    
    private void createProjectAssignments() {
        LocalDate today = LocalDate.now();
        
        // Employee1: Active on project1 (first project)
        ProjectAssignment assignment1 = ProjectAssignment.builder()
                .user(employees.get(0)) // employee1
                .project(projects.get(0)) // project1
                .assignedDate(today.minusMonths(2))
                .role("Junior Developer")
                .isActive(true)
                .build();
        assignmentRepository.save(assignment1);
        employees.get(0).setIsOnBench(false);
        userRepository.save(employees.get(0));
        
        // Employee2: Active on project1 (first project)
        ProjectAssignment assignment2 = ProjectAssignment.builder()
                .user(employees.get(1)) // employee2
                .project(projects.get(0)) // project1
                .assignedDate(today.minusMonths(2))
                .role("Senior Developer")
                .isActive(true)
                .build();
        assignmentRepository.save(assignment2);
        employees.get(1).setIsOnBench(false);
        userRepository.save(employees.get(1));
        
        // Employee3: Previously on project2 (completed), now on project1 (second project)
        // First assignment (completed project)
        ProjectAssignment assignment3a = ProjectAssignment.builder()
                .user(employees.get(2)) // employee3
                .project(projects.get(1)) // project2 (completed)
                .assignedDate(today.minusMonths(8))
                .unassignedDate(today.minusMonths(2))
                .role("Developer")
                .isActive(false)
                .build();
        assignmentRepository.save(assignment3a);
        
        // Second assignment (current active project)
        ProjectAssignment assignment3b = ProjectAssignment.builder()
                .user(employees.get(2)) // employee3
                .project(projects.get(0)) // project1
                .assignedDate(today.minusMonths(1))
                .role("Lead Developer")
                .isActive(true)
                .build();
        assignmentRepository.save(assignment3b);
        employees.get(2).setIsOnBench(false);
        userRepository.save(employees.get(2));
        
        // Employee4: Finished project2, now on bench
        ProjectAssignment assignment4 = ProjectAssignment.builder()
                .user(employees.get(3)) // employee4
                .project(projects.get(1)) // project2 (completed)
                .assignedDate(today.minusMonths(7))
                .unassignedDate(today.minusMonths(2))
                .role("QA Engineer")
                .isActive(false)
                .build();
        assignmentRepository.save(assignment4);
        employees.get(3).setIsOnBench(true);
        userRepository.save(employees.get(3));
        
        // Employee5: Active on MULTIPLE projects simultaneously (project3 and project4)
        // This tests the scenario where employees work on multiple projects at once
        ProjectAssignment assignment5a = ProjectAssignment.builder()
                .user(employees.get(4)) // employee5
                .project(projects.get(2)) // project3
                .assignedDate(today.minusMonths(1))
                .role("Backend Developer")
                .isActive(true)
                .build();
        assignmentRepository.save(assignment5a);
        
        ProjectAssignment assignment5b = ProjectAssignment.builder()
                .user(employees.get(4)) // employee5
                .project(projects.get(3)) // project4
                .assignedDate(today.minusMonths(1))
                .role("Frontend Developer")
                .isActive(true)
                .build();
        assignmentRepository.save(assignment5b);
        employees.get(4).setIsOnBench(false);
        userRepository.save(employees.get(4));
        
        log.info("Created project assignments for all employees");
    }
    
    private void createTimesheets() {
        LocalDate today = LocalDate.now();
        
        // Create timesheets for the past 3 months for active employees
        for (int monthOffset = 0; monthOffset < 3; monthOffset++) {
            LocalDate monthStart = today.minusMonths(monthOffset).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            if (monthEnd.isAfter(today)) {
                monthEnd = today;
            }
            
            for (int i = 0; i < 3; i++) { // First 3 employees are active
                User employee = employees.get(i);
                Project project = projects.get(0); // project1
                
                // Create timesheets for each working day
                LocalDate date = monthStart;
                while (!date.isAfter(monthEnd)) {
                    // Skip weekends
                    if (date.getDayOfWeek().getValue() < 6) {
                        Timesheet.TimesheetStatus status;
                        if (monthOffset == 0) {
                            // Current month: mix of statuses
                            int dayOfMonth = date.getDayOfMonth();
                            if (dayOfMonth <= 10) {
                                status = Timesheet.TimesheetStatus.APPROVED;
                            } else if (dayOfMonth <= 20) {
                                status = Timesheet.TimesheetStatus.SUBMITTED;
                            } else {
                                status = Timesheet.TimesheetStatus.DRAFT;
                            }
                        } else {
                            // Past months: all approved
                            status = Timesheet.TimesheetStatus.APPROVED;
                        }
                        
                        Timesheet timesheet = Timesheet.builder()
                                .user(employee)
                                .project(project)
                                .projectCode(project.getCode())
                                .date(date)
                                .hours(8.0)
                                .description("Working on " + project.getName())
                                .status(status)
                                .organization(organization)
                                .approvedBy(status == Timesheet.TimesheetStatus.APPROVED ? manager1 : null)
                                .approvedAt(status == Timesheet.TimesheetStatus.APPROVED ? LocalDateTime.now().minusDays(1) : null)
                                .submittedAt(status != Timesheet.TimesheetStatus.DRAFT ? LocalDateTime.now().minusDays(2) : null)
                                .build();
                        timesheetRepository.save(timesheet);
                    }
                    date = date.plusDays(1);
                }
            }
            
            // Employee4: Had timesheets on project2 before it completed
            if (monthOffset >= 2) {
                User employee4 = employees.get(3);
                Project project2 = projects.get(1);
                
                LocalDate date = monthStart;
                while (!date.isAfter(monthEnd)) {
                    if (date.getDayOfWeek().getValue() < 6 && date.isBefore(today.minusMonths(2))) {
                        Timesheet timesheet = Timesheet.builder()
                                .user(employee4)
                                .project(project2)
                                .projectCode(project2.getCode())
                                .date(date)
                                .hours(8.0)
                                .description("QA testing on " + project2.getName())
                                .status(Timesheet.TimesheetStatus.APPROVED)
                                .organization(organization)
                                .approvedBy(manager1)
                                .approvedAt(LocalDateTime.now().minusMonths(2))
                                .submittedAt(LocalDateTime.now().minusMonths(2).minusDays(1))
                                .build();
                        timesheetRepository.save(timesheet);
                    }
                    date = date.plusDays(1);
                }
            }
        }
        
        log.info("Created timesheets for past 3 months");
    }
    
    private void createPayroll() {
        LocalDate today = LocalDate.now();
        
        // Create payroll for past 3 months for all employees
        for (int monthOffset = 1; monthOffset <= 3; monthOffset++) {
            YearMonth payrollMonth = YearMonth.from(today.minusMonths(monthOffset));
            int month = payrollMonth.getMonthValue();
            int year = payrollMonth.getYear();
            int workingDays = calculateWorkingDays(payrollMonth);
            
            for (User employee : employees) {
                BigDecimal baseSalary = employee.getMonthlySalary();
                int daysWorked = workingDays;
                int paidLeaves = 0;
                int unpaidLeaves = 0;
                
                // Vary the data slightly
                if (employee.getEmail().contains("employee4")) {
                    // Employee4 had some unpaid leaves last month
                    if (monthOffset == 1) {
                        unpaidLeaves = 2;
                        daysWorked = workingDays - unpaidLeaves;
                    }
                }
                if (employee.getEmail().contains("employee3")) {
                    // Employee3 took some paid leaves
                    paidLeaves = 1;
                }
                
                // Calculate deductions for unpaid leaves
                BigDecimal dailyRate = baseSalary.divide(new BigDecimal(workingDays), 2, RoundingMode.HALF_UP);
                BigDecimal deductions = dailyRate.multiply(new BigDecimal(unpaidLeaves));
                BigDecimal netSalary = baseSalary.subtract(deductions);
                
                Payroll payroll = Payroll.builder()
                        .user(employee)
                        .organization(organization)
                        .periodMonth(month)
                        .periodYear(year)
                        .daysWorked(daysWorked)
                        .totalDaysInMonth(workingDays)
                        .paidLeavesUsed(paidLeaves)
                        .unpaidLeavesUsed(unpaidLeaves)
                        .baseSalary(baseSalary)
                        .deductions(deductions)
                        .netSalary(netSalary)
                        .status(monthOffset == 1 ? Payroll.PayrollStatus.PROCESSED : Payroll.PayrollStatus.PAID)
                        .processedBy(hr1)
                        .processedAt(LocalDateTime.now().minusDays(monthOffset * 5L))
                        .build();
                payrollRepository.save(payroll);
            }
            
            log.info("Created payroll for {}/{}", month, year);
        }
    }
    
    private int calculateWorkingDays(YearMonth yearMonth) {
        int workingDays = 0;
        LocalDate date = yearMonth.atDay(1);
        while (date.getMonth() == yearMonth.getMonth()) {
            if (date.getDayOfWeek().getValue() < 6) {
                workingDays++;
            }
            date = date.plusDays(1);
        }
        return workingDays;
    }
    
    private void createExpenses() {
        LocalDate today = LocalDate.now();
        Expense.ExpenseCategory[] categories = Expense.ExpenseCategory.values();
        
        for (int i = 0; i < 3; i++) { // First 3 employees
            User employee = employees.get(i);
            
            // Create 3 expenses per employee
            for (int j = 0; j < 3; j++) {
                Expense.ExpenseStatus status;
                switch (j) {
                    case 0:
                        status = Expense.ExpenseStatus.APPROVED;
                        break;
                    case 1:
                        status = Expense.ExpenseStatus.SUBMITTED;
                        break;
                    default:
                        status = Expense.ExpenseStatus.PENDING;
                }
                
                Expense expense = Expense.builder()
                        .description("Business expense #" + (j + 1) + " for " + employee.getFirstName() + " " + employee.getLastName())
                        .amount(new BigDecimal(150 + (j * 75)).setScale(2, RoundingMode.HALF_UP))
                        .expenseDate(today.minusDays(j * 7L))
                        .category(categories[j % categories.length])
                        .status(status)
                        .user(employee)
                        .organization(organization)
                        .build();
                expenseRepository.save(expense);
            }
        }
        
        log.info("Created expense records");
    }
    
    private void createLeaveRequests() {
        LocalDate today = LocalDate.now();
        
        for (int i = 0; i < 4; i++) { // First 4 employees
            User employee = employees.get(i);
            
            // Create 2 leave requests per employee
            LeaveRequest.LeaveType[] leaveTypes = {
                LeaveRequest.LeaveType.VACATION,
                LeaveRequest.LeaveType.SICK_LEAVE
            };
            
            for (int j = 0; j < 2; j++) {
                LocalDate startDate = today.minusDays(45 - (j * 15L));
                LocalDate endDate = startDate.plusDays(2);
                boolean isApproved = j == 0;
                boolean isPaid = leaveTypes[j] != LeaveRequest.LeaveType.UNPAID_LEAVE;
                
                LeaveRequest leave = LeaveRequest.builder()
                        .user(employee)
                        .organization(organization)
                        .leaveType(leaveTypes[j])
                        .startDate(startDate)
                        .endDate(endDate)
                        .numberOfDays(3)
                        .reason("Leave request for " + leaveTypes[j].name().toLowerCase().replace("_", " "))
                        .status(isApproved ? LeaveRequest.LeaveStatus.APPROVED : LeaveRequest.LeaveStatus.PENDING)
                        .approvedBy(isApproved ? manager1 : null)
                        .approvedAt(isApproved ? LocalDateTime.now().minusDays(40) : null)
                        .isPaid(isPaid)
                        .paidDays(isPaid ? 3 : 0)
                        .unpaidDays(isPaid ? 0 : 3)
                        .build();
                leaveRequestRepository.save(leave);
            }
        }
        
        log.info("Created leave request records");
    }
    
    private void createActivityLogs() {
        LocalDateTime now = LocalDateTime.now();
        
        // Create sample activity logs
        List<ActivityLog> logs = new ArrayList<>();
        
        // Organization created
        logs.add(ActivityLog.builder()
                .user(admin)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.ORGANIZATION_CREATED)
                .description("Organization TechCorp Solutions was created")
                .entityType("Organization")
                .entityId(organization.getId())
                .createdAt(now.minusDays(30))
                .build());
        
        // User creation logs
        logs.add(ActivityLog.builder()
                .user(admin)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.USER_CREATED)
                .description("User Manager One was created with role MANAGER")
                .entityType("User")
                .entityId(manager1.getId())
                .createdAt(now.minusDays(29))
                .build());
        
        logs.add(ActivityLog.builder()
                .user(admin)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.USER_CREATED)
                .description("User HR One was created with role HR")
                .entityType("User")
                .entityId(hr1.getId())
                .createdAt(now.minusDays(29))
                .build());
        
        logs.add(ActivityLog.builder()
                .user(admin)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.USER_CREATED)
                .description("User Finance One was created with role FINANCE")
                .entityType("User")
                .entityId(finance1.getId())
                .createdAt(now.minusDays(29))
                .build());
        
        // Employee creation logs
        for (User employee : employees) {
            logs.add(ActivityLog.builder()
                    .user(hr1)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.USER_CREATED)
                    .description("Employee " + employee.getFirstName() + " " + employee.getLastName() + " was onboarded")
                    .entityType("User")
                    .entityId(employee.getId())
                    .createdAt(now.minusDays(28))
                    .build());
        }
        
        // Project creation logs
        for (Project project : projects) {
            logs.add(ActivityLog.builder()
                    .user(hr1)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.PROJECT_CREATED)
                    .description("Project " + project.getName() + " (" + project.getCode() + ") was created")
                    .entityType("Project")
                    .entityId(project.getId())
                    .createdAt(now.minusDays(27))
                    .build());
        }
        
        // Expense logs
        for (int i = 0; i < 3; i++) {
            User employee = employees.get(i);
            logs.add(ActivityLog.builder()
                    .user(employee)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.EXPENSE_CREATED)
                    .description("Expense created by " + employee.getFirstName() + " " + employee.getLastName())
                    .entityType("Expense")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(15 - i))
                    .build());
            
            logs.add(ActivityLog.builder()
                    .user(employee)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.EXPENSE_SUBMITTED)
                    .description("Expense submitted for approval by " + employee.getFirstName())
                    .entityType("Expense")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(14 - i))
                    .build());
            
            if (i == 0) {
                logs.add(ActivityLog.builder()
                        .user(manager1)
                        .organization(organization)
                        .activityType(ActivityLog.ActivityType.EXPENSE_APPROVED)
                        .description("Expense approved by Manager One")
                        .entityType("Expense")
                        .entityId(1L)
                        .createdAt(now.minusDays(13))
                        .build());
            }
        }
        
        // Leave request logs
        for (int i = 0; i < 3; i++) {
            User employee = employees.get(i);
            logs.add(ActivityLog.builder()
                    .user(employee)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.LEAVE_REQUEST_CREATED)
                    .description("Leave request created by " + employee.getFirstName() + " " + employee.getLastName())
                    .entityType("LeaveRequest")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(20 - i))
                    .build());
            
            logs.add(ActivityLog.builder()
                    .user(manager1)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.LEAVE_REQUEST_APPROVED)
                    .description("Leave request approved by Manager One")
                    .entityType("LeaveRequest")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(18 - i))
                    .build());
        }
        
        // Timesheet logs
        for (int i = 0; i < 3; i++) {
            User employee = employees.get(i);
            logs.add(ActivityLog.builder()
                    .user(employee)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.TIMESHEET_SUBMITTED)
                    .description("Weekly timesheet submitted by " + employee.getFirstName())
                    .entityType("Timesheet")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(7 - i))
                    .build());
            
            logs.add(ActivityLog.builder()
                    .user(manager1)
                    .organization(organization)
                    .activityType(ActivityLog.ActivityType.TIMESHEET_APPROVED)
                    .description("Timesheet approved by Manager One")
                    .entityType("Timesheet")
                    .entityId((long) (i + 1))
                    .createdAt(now.minusDays(5 - i))
                    .build());
        }
        
        // Payroll logs
        logs.add(ActivityLog.builder()
                .user(hr1)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.PAYROLL_CALCULATED)
                .description("Monthly payroll calculated for all employees")
                .entityType("Payroll")
                .entityId(1L)
                .createdAt(now.minusDays(3))
                .build());
        
        logs.add(ActivityLog.builder()
                .user(finance1)
                .organization(organization)
                .activityType(ActivityLog.ActivityType.PAYROLL_PROCESSED)
                .description("Payroll processed and ready for disbursement")
                .entityType("Payroll")
                .entityId(1L)
                .createdAt(now.minusDays(2))
                .build());
        
        // Save all logs
        activityLogRepository.saveAll(logs);
        log.info("Created {} activity log records", logs.size());
    }
    
    private void logSummary() {
        log.info("=== DATA SEEDING SUMMARY ===");
        log.info("Organization: {} (subdomain: {})", organization.getName(), organization.getSubdomain());
        log.info("Users created:");
        log.info("  - Admin: admin@techcorp.com / admin123");
        log.info("  - Manager: manager1@techcorp.com / manager123");
        log.info("  - HR: hr1@techcorp.com / hr123");
        log.info("  - Finance: finance1@techcorp.com / finance123");
        log.info("  - Employees: employee1-5@techcorp.com / employee123 (same password for all 5)");
        log.info("Projects: {} projects (PROJ001-PROJ005)", projects.size());
        log.info("Employee scenarios:");
        log.info("  - Employee1 & Employee2: Active on PROJ001 (first project)");
        log.info("  - Employee3: Active on PROJ001 (second project, finished PROJ002)");
        log.info("  - Employee4: Finished PROJ002, now on bench");
        log.info("  - Employee5: Never assigned, on bench");
        log.info("Payroll: 3 months of history for all employees");
        log.info("================================");
    }
}

package com.expensemanagement.controller;

import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class RoleBasedAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private Organization testOrg;
    private User employeeUser;
    private User managerUser;
    private User adminUser;
    private User financeUser;
    private Expense employeeExpense;
    private Expense managerExpense;

    @BeforeEach
    void setUp() {
        // Create test organization with unique subdomain to avoid conflicts
        String uniqueSubdomain = "testorg" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
        testOrg = Organization.builder()
                .name("Test Organization")
                .subdomain(uniqueSubdomain)
                .address("123 Test St")
                .contactEmail("test@test.com")
                .contactPhone("123-456-7890")
                .build();
        testOrg = organizationRepository.save(testOrg);

        // Create users with different roles
        managerUser = User.builder()
                .firstName("Jane")
                .lastName("Manager")
                .email("manager@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(User.UserRole.MANAGER)
                .organization(testOrg)
                .build();
        managerUser = userRepository.save(managerUser);

        // employeeUser must have managerUser as its assigned manager: the
        // approval workflow only lets an employee's own manager approve or
        // reject their expenses (see ApprovalService.approveExpense), so an
        // orphaned employee would make every approval test fail with a 400.
        employeeUser = User.builder()
                .firstName("John")
                .lastName("Employee")
                .email("employee@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrg)
                .manager(managerUser)
                .build();
        employeeUser = userRepository.save(employeeUser);

        adminUser = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(User.UserRole.ADMIN)
                .organization(testOrg)
                .build();
        adminUser = userRepository.save(adminUser);

        financeUser = User.builder()
                .firstName("Finance")
                .lastName("User")
                .email("finance@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(User.UserRole.FINANCE)
                .organization(testOrg)
                .build();
        financeUser = userRepository.save(financeUser);

        // Create test expenses
        employeeExpense = Expense.builder()
                .description("Employee Expense")
                .amount(new BigDecimal("100.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.TRAVEL)
                .status(Expense.ExpenseStatus.PENDING)
                .user(employeeUser)
                .organization(testOrg)
                .build();
        employeeExpense = expenseRepository.save(employeeExpense);

        managerExpense = Expense.builder()
                .description("Manager Expense")
                .amount(new BigDecimal("200.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.MEALS)
                .status(Expense.ExpenseStatus.PENDING)
                .user(managerUser)
                .organization(testOrg)
                .build();
        managerExpense = expenseRepository.save(managerExpense);
    }

    private String getToken(User user) {
        org.springframework.security.core.Authentication auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getPassword()
            );
        return tokenProvider.generateToken(auth, user.getId(), user.getOrganization().getId(), user.getRole().name());
    }

    @Test
    void testEmployeeCanOnlySeeOwnExpenses() throws Exception {
        String token = getToken(employeeUser);

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.description == 'Employee Expense')]").exists())
                .andExpect(jsonPath("$.content[?(@.description == 'Manager Expense')]").doesNotExist());
    }

    @Test
    void testManagerCanSeeOnlyOwnExpensesOnExpensesPage() throws Exception {
        // The Expenses page is a personal ledger for every role except
        // Admin -- a manager reviews their reports' spending through the
        // Approvals page instead (see ExpenseController.getExpenses).
        String token = getToken(managerUser);

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.description == 'Manager Expense')]").exists())
                .andExpect(jsonPath("$.content[?(@.description == 'Employee Expense')]").doesNotExist());
    }

    @Test
    void testAdminCanSeeAllExpenses() throws Exception {
        String token = getToken(adminUser);

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.description == 'Employee Expense')]").exists())
                .andExpect(jsonPath("$.content[?(@.description == 'Manager Expense')]").exists());
    }

    @Test
    void testFinanceCannotSeeOthersExpensesOnExpensesPage() throws Exception {
        // Finance settles approved expenses (see FinanceService) but has no
        // business reason to browse every employee's personal spending on
        // the Expenses page, so it is scoped the same as any other
        // non-admin role.
        String token = getToken(financeUser);

        mockMvc.perform(get("/api/expenses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.description == 'Employee Expense')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.description == 'Manager Expense')]").doesNotExist());
    }

    @Test
    void testEmployeeCannotAccessApprovals() throws Exception {
        String token = getToken(employeeUser);

        mockMvc.perform(get("/api/approvals/pending")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testManagerCanAccessApprovals() throws Exception {
        String token = getToken(managerUser);

        mockMvc.perform(get("/api/approvals/pending")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testAdminCannotAccessApprovals() throws Exception {
        // Expense approval is scoped to the employee's direct manager only
        // (see ApprovalService.approveExpense) -- Admin manages
        // organization-wide resources but does not sit in anyone's approval
        // chain, matching the frontend's approvalRoles: ['MANAGER'] config.
        String token = getToken(adminUser);

        mockMvc.perform(get("/api/approvals/pending")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testFinanceCannotAccessApprovals() throws Exception {
        // Finance settles already-approved expenses; it never sits in the
        // approval chain itself.
        String token = getToken(financeUser);

        mockMvc.perform(get("/api/approvals/pending")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEmployeeCannotAccessAnalytics() throws Exception {
        String token = getToken(employeeUser);

        mockMvc.perform(get("/api/analytics")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testManagerCanAccessAnalytics() throws Exception {
        String token = getToken(managerUser);

        mockMvc.perform(get("/api/analytics")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testEmployeeCannotApproveExpense() throws Exception {
        String token = getToken(employeeUser);

        // Submit expense first
        expenseRepository.findById(employeeExpense.getId()).ifPresent(expense -> {
            expense.setStatus(Expense.ExpenseStatus.SUBMITTED);
            expenseRepository.save(expense);
        });

        mockMvc.perform(post("/api/approvals/" + employeeExpense.getId() + "/approve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comments\":\"Approved\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testManagerCanApproveExpense() throws Exception {
        String token = getToken(managerUser);

        // Submit expense first
        expenseRepository.findById(employeeExpense.getId()).ifPresent(expense -> {
            expense.setStatus(Expense.ExpenseStatus.SUBMITTED);
            expenseRepository.save(expense);
        });

        mockMvc.perform(post("/api/approvals/" + employeeExpense.getId() + "/approve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comments\":\"Approved\"}"))
                .andExpect(status().isOk());
    }
}


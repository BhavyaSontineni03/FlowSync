package com.expensemanagement.service;

import com.expensemanagement.dto.AnalyticsDto;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsService.
 * Tests analytics calculations and caching.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Organization testOrganization;
    private User testUser;
    private List<Expense> testExpenses;

    @BeforeEach
    void setUp() {
        testOrganization = Organization.builder()
                .id(1L)
                .name("Test Org")
                .build();

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .organization(testOrganization)
                .build();

        testExpenses = Arrays.asList(
                Expense.builder()
                        .id(1L)
                        .description("Expense 1")
                        .amount(new BigDecimal("100.00"))
                        .expenseDate(LocalDate.now().minusDays(10))
                        .category(Expense.ExpenseCategory.TRAVEL)
                        .status(Expense.ExpenseStatus.APPROVED)
                        .user(testUser)
                        .organization(testOrganization)
                        .build(),
                Expense.builder()
                        .id(2L)
                        .description("Expense 2")
                        .amount(new BigDecimal("200.00"))
                        .expenseDate(LocalDate.now().minusDays(5))
                        .category(Expense.ExpenseCategory.MEALS)
                        // SUBMITTED, not PENDING: the Manager view in
                        // AnalyticsService treats "pending" as pending
                        // approval (SUBMITTED), not an unsubmitted draft
                        // (PENDING) -- see AnalyticsService.getAnalytics.
                        .status(Expense.ExpenseStatus.SUBMITTED)
                        .user(testUser)
                        .organization(testOrganization)
                        .build(),
                Expense.builder()
                        .id(3L)
                        .description("Expense 3")
                        .amount(new BigDecimal("50.00"))
                        .expenseDate(LocalDate.now().minusDays(2))
                        .category(Expense.ExpenseCategory.TRAVEL)
                        .status(Expense.ExpenseStatus.REJECTED)
                        .user(testUser)
                        .organization(testOrganization)
                        .build()
        );
    }

    @Test
    void testGetAnalytics_Success() {
        // Given
        LocalDate startDate = LocalDate.now().minusMonths(1);
        LocalDate endDate = LocalDate.now();
        when(expenseRepository.findByOrganizationIdAndDateRange(eq(1L), eq(startDate), eq(endDate)))
                .thenReturn(testExpenses);

        // When
        AnalyticsDto result = analyticsService.getAnalytics(1L, startDate, endDate, null);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("350.00"), result.getTotalExpenses());
        assertEquals(new BigDecimal("200.00"), result.getPendingExpenses());
        assertEquals(new BigDecimal("100.00"), result.getApprovedExpenses());
        assertEquals(new BigDecimal("50.00"), result.getRejectedExpenses());
        assertEquals(3L, result.getTotalCount());
        assertNotNull(result.getExpensesByCategory());
        assertNotNull(result.getExpensesByMonth());
        assertNotNull(result.getExpensesByStatus());
    }

    @Test
    void testGetAnalytics_DefaultDateRange() {
        // Given
        when(expenseRepository.findByOrganizationIdAndDateRange(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(testExpenses);

        // When
        AnalyticsDto result = analyticsService.getAnalytics(1L, null, null, null);

        // Then
        assertNotNull(result);
        verify(expenseRepository, times(1)).findByOrganizationIdAndDateRange(
                eq(1L), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void testGetAnalytics_EmptyExpenses() {
        // Given
        when(expenseRepository.findByOrganizationIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(Arrays.asList());

        // When
        AnalyticsDto result = analyticsService.getAnalytics(1L, LocalDate.now().minusMonths(1), LocalDate.now(), null);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalExpenses());
        assertEquals(0L, result.getTotalCount());
        assertTrue(result.getExpensesByCategory().isEmpty());
    }

    @Test
    void testGetAnalytics_CategoryBreakdown() {
        // Given
        when(expenseRepository.findByOrganizationIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(testExpenses);

        // When
        AnalyticsDto result = analyticsService.getAnalytics(1L, LocalDate.now().minusMonths(1), LocalDate.now(), null);

        // Then
        assertNotNull(result.getExpensesByCategory());
        assertTrue(result.getExpensesByCategory().size() > 0);
        // Verify TRAVEL category has 2 expenses (100 + 50 = 150)
        boolean foundTravel = result.getExpensesByCategory().stream()
                .anyMatch(c -> c.getCategory().equals("TRAVEL") && 
                        c.getAmount().equals(new BigDecimal("150.00")));
        assertTrue(foundTravel);
    }
}


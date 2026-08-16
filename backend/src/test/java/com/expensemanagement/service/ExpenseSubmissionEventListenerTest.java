package com.expensemanagement.service;

import com.expensemanagement.event.ExpenseSubmittedEvent;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Post-commit submit side effects that used to run inline in submitExpense.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseSubmissionEventListenerTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WebSocketService webSocketService;

    private ExpenseSubmissionEventListener listener;

    private Expense testExpense;

    @BeforeEach
    void setUp() {
        listener = new ExpenseSubmissionEventListener(
                expenseRepository, activityLogService, notificationService, webSocketService);

        Organization org = Organization.builder().id(1L).name("Test Org").build();
        User user = User.builder().id(1L).firstName("John").lastName("Doe").organization(org).build();

        testExpense = Expense.builder()
                .id(10L)
                .user(user)
                .organization(org)
                .amount(new BigDecimal("100.00"))
                .category(Expense.ExpenseCategory.TRAVEL)
                .description("Flight")
                .expenseDate(LocalDate.now())
                .status(Expense.ExpenseStatus.SUBMITTED)
                .build();
    }

    @Test
    void onExpenseSubmitted_runsSideEffectsForExistingExpense() {
        when(expenseRepository.findByIdWithUserOrgAndManager(10L)).thenReturn(Optional.of(testExpense));

        listener.onExpenseSubmitted(new ExpenseSubmittedEvent(10L, 1L));

        verify(activityLogService, times(1))
                .logActivity(any(), any(), eq(testExpense.getUser()), eq(testExpense.getOrganization()),
                        eq("Expense"), eq(10L), any());
        verify(notificationService, times(1)).notifyManagersForApproval(testExpense);
        verify(webSocketService, times(1)).sendExpenseUpdate(eq(1L), any());
    }

    @Test
    void onExpenseSubmitted_expenseMissing_doesNotThrowOrNotify() {
        when(expenseRepository.findByIdWithUserOrgAndManager(99L)).thenReturn(Optional.empty());

        listener.onExpenseSubmitted(new ExpenseSubmittedEvent(99L, 1L));

        verifyNoInteractions(notificationService, webSocketService, activityLogService);
    }

    @Test
    void onExpenseSubmitted_notificationFailure_isRetriedThenSwallowed() {
        when(expenseRepository.findByIdWithUserOrgAndManager(10L)).thenReturn(Optional.of(testExpense));
        doThrow(new RuntimeException("SMTP down")).when(notificationService).notifyManagersForApproval(any());

        // Must not propagate after an already-committed submission.
        assertDoesNotThrow(() -> listener.onExpenseSubmitted(new ExpenseSubmittedEvent(10L, 1L)));

        verify(notificationService, times(2)).notifyManagersForApproval(testExpense);
        // Isolated effects: activity + websocket still run once each.
        verify(activityLogService, times(1)).logActivity(any(), any(), any(), any(), any(), any(), any());
        verify(webSocketService, times(1)).sendExpenseUpdate(eq(1L), any());
    }

    @Test
    void onExpenseSubmitted_transientNotifyFailure_recoversOnSecondAttempt() {
        when(expenseRepository.findByIdWithUserOrgAndManager(10L)).thenReturn(Optional.of(testExpense));
        doThrow(new RuntimeException("SMTP blip"))
                .doNothing()
                .when(notificationService).notifyManagersForApproval(any());

        assertDoesNotThrow(() -> listener.onExpenseSubmitted(new ExpenseSubmittedEvent(10L, 1L)));

        verify(notificationService, times(2)).notifyManagersForApproval(testExpense);
        verify(webSocketService, times(1)).sendExpenseUpdate(eq(1L), any());
    }

    @Test
    void runWithRetry_swallowsAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertDoesNotThrow(() -> listener.runWithRetry("probe", 42L, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("always fail");
        }));
        assertEquals(2, calls.get());
    }
}

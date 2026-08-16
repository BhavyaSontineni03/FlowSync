package com.expensemanagement.saga;

import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.OrgBudget;
import com.expensemanagement.model.PaymentLedgerEntry;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrgBudgetRepository;
import com.expensemanagement.repository.PaymentLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSagaStepTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private OrgBudgetRepository orgBudgetRepository;
    @Mock private PaymentLedgerEntryRepository ledgerRepository;
    @Mock private EventPublisher eventPublisher;

    private PaymentSagaStep step;

    @BeforeEach
    void setUp() {
        step = new PaymentSagaStep(expenseRepository, orgBudgetRepository, ledgerRepository, eventPublisher);
    }

    private Expense approvedExpense(Long id, BigDecimal amount) {
        return Expense.builder()
                .id(id)
                .amount(amount)
                .status(Expense.ExpenseStatus.APPROVED)
                .build();
    }

    private OrgBudget budgetWithRemaining(BigDecimal allocated, BigDecimal consumed) {
        LocalDate now = LocalDate.now();
        return OrgBudget.builder()
                .id(1L)
                .periodYear(now.getYear())
                .periodMonth(now.getMonthValue())
                .allocatedAmount(allocated)
                .consumedAmount(consumed)
                .build();
    }

    @Test
    void execute_withSufficientBudget_marksExpensePaidAndReservesBudget() {
        Expense expense = approvedExpense(1L, new BigDecimal("200.00"));
        OrgBudget budget = budgetWithRemaining(new BigDecimal("1000.00"), new BigDecimal("100.00"));

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(orgBudgetRepository.findByOrganizationIdAndPeriodYearAndPeriodMonth(eq(5L), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));
        when(orgBudgetRepository.saveAndFlush(any(OrgBudget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.save(any(PaymentLedgerEntry.class))).thenAnswer(inv -> {
            PaymentLedgerEntry entry = inv.getArgument(0);
            entry.setId(99L);
            return entry;
        });

        ExpenseSagaContext context = new ExpenseSagaContext(1L, 5L, new BigDecimal("200.00"));
        step.execute(context);

        assertEquals(Expense.ExpenseStatus.PAID, expense.getStatus());
        assertTrue(context.isBudgetReserved());
        assertEquals(99L, context.getPaymentLedgerEntryId());
        assertEquals(new BigDecimal("300.00"), budget.getConsumedAmount());
        verify(eventPublisher).publish(eq(com.expensemanagement.event.EventType.EXPENSE_PAID), eq("Expense"), eq(1L), eq(5L), any());
    }

    @Test
    void execute_budgetExceeded_throwsAndLeavesExpenseUntouched() {
        Expense expense = approvedExpense(2L, new BigDecimal("500.00"));
        OrgBudget budget = budgetWithRemaining(new BigDecimal("1000.00"), new BigDecimal("900.00")); // only 100 remaining

        when(expenseRepository.findById(2L)).thenReturn(Optional.of(expense));
        when(orgBudgetRepository.findByOrganizationIdAndPeriodYearAndPeriodMonth(eq(5L), anyInt(), anyInt()))
                .thenReturn(Optional.of(budget));

        ExpenseSagaContext context = new ExpenseSagaContext(2L, 5L, new BigDecimal("500.00"));

        assertThrows(SagaStepException.class, () -> step.execute(context));
        assertEquals(Expense.ExpenseStatus.APPROVED, expense.getStatus(), "expense must not be marked PAID when the reservation failed");
        assertFalse(context.isBudgetReserved());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void execute_expenseNotApproved_refusesToPay() {
        Expense expense = Expense.builder().id(3L).amount(BigDecimal.TEN).status(Expense.ExpenseStatus.PENDING).build();
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));

        ExpenseSagaContext context = new ExpenseSagaContext(3L, 5L, BigDecimal.TEN);

        assertThrows(SagaStepException.class, () -> step.execute(context));
        verify(orgBudgetRepository, never()).findByOrganizationIdAndPeriodYearAndPeriodMonth(any(), anyInt(), anyInt());
    }

    @Test
    void compensate_afterReservedPayment_reversesLedgerBudgetAndExpenseStatus() {
        OrgBudget budget = budgetWithRemaining(new BigDecimal("1000.00"), new BigDecimal("300.00"));
        PaymentLedgerEntry ledger = PaymentLedgerEntry.builder().id(99L).status(PaymentLedgerEntry.LedgerStatus.COMPLETED).build();
        Expense expense = approvedExpense(1L, new BigDecimal("200.00"));
        expense.setStatus(Expense.ExpenseStatus.PAID);

        when(orgBudgetRepository.findById(1L)).thenReturn(Optional.of(budget));
        when(ledgerRepository.findById(99L)).thenReturn(Optional.of(ledger));
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

        ExpenseSagaContext context = new ExpenseSagaContext(1L, 5L, new BigDecimal("200.00"));
        context.setBudgetReserved(true);
        context.setOrgBudgetId(1L);
        context.setPaymentLedgerEntryId(99L);

        step.compensate(context);

        assertEquals(new BigDecimal("100.00"), budget.getConsumedAmount());
        assertEquals(PaymentLedgerEntry.LedgerStatus.REVERSED, ledger.getStatus());
        assertEquals(Expense.ExpenseStatus.APPROVED, expense.getStatus());
        verify(eventPublisher).publish(eq(com.expensemanagement.event.EventType.EXPENSE_PAYMENT_COMPENSATED), eq("Expense"), eq(1L), eq(5L), any());
    }

    @Test
    void compensate_whenBudgetWasNeverReserved_doesNothing() {
        ExpenseSagaContext context = new ExpenseSagaContext(1L, 5L, new BigDecimal("200.00"));
        // budgetReserved defaults to false

        step.compensate(context);

        verifyNoInteractions(orgBudgetRepository, ledgerRepository, eventPublisher);
    }
}

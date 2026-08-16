package com.expensemanagement.saga;

import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.EventType;
import com.expensemanagement.grpc.AnomalyScoreQuery;
import com.expensemanagement.grpc.AnomalyScoreResult;
import com.expensemanagement.grpc.GrpcAnomalyScoringClient;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.ExpenseAnomalyAssessment;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseAnomalyAssessmentRepository;
import com.expensemanagement.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyScoringSagaStepTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseAnomalyAssessmentRepository assessmentRepository;
    @Mock private GrpcAnomalyScoringClient scoringClient;
    @Mock private EventPublisher eventPublisher;

    private AnomalyScoringSagaStep step;

    @BeforeEach
    void setUp() {
        step = new AnomalyScoringSagaStep(expenseRepository, assessmentRepository, scoringClient, eventPublisher, new ObjectMapper());
    }

    private Expense expense(Long id) {
        Organization org = Organization.builder().id(6L).build();
        User user = User.builder().id(20L).build();
        return Expense.builder()
                .id(id)
                .organization(org)
                .user(user)
                .amount(new BigDecimal("75.00"))
                .category(Expense.ExpenseCategory.MEALS)
                .description("Team lunch")
                .expenseDate(LocalDate.now())
                .build();
    }

    @Test
    void execute_normalScore_doesNotFlagForReview() {
        Expense expense = expense(1L);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
        when(expenseRepository.findTop20ByUserIdOrderByCreatedAtDesc(20L)).thenReturn(Collections.emptyList());
        when(expenseRepository.findTop20ByUserIdAndCategoryOrderByExpenseDateDesc(20L, Expense.ExpenseCategory.MEALS))
                .thenReturn(Collections.emptyList());
        when(scoringClient.score(any(AnomalyScoreQuery.class)))
                .thenReturn(new AnomalyScoreResult(true, 0.12, false, 30.0, Map.of("amount_zscore", 0.1), "isolation-forest-v1"));

        ExpenseSagaContext context = new ExpenseSagaContext(1L, 6L, new BigDecimal("75.00"));
        step.execute(context);

        assertFalse(context.isFlaggedForReview());
        assertEquals(0.12, context.getAnomalyScore());

        ArgumentCaptor<ExpenseAnomalyAssessment> captor = ArgumentCaptor.forClass(ExpenseAnomalyAssessment.class);
        verify(assessmentRepository).save(captor.capture());
        assertFalse(captor.getValue().isScoringUnavailable());
        assertFalse(captor.getValue().getIsAnomalous());

        verify(eventPublisher).publish(eq(EventType.EXPENSE_SCORED), eq("Expense"), eq(1L), eq(6L), any());
        verify(eventPublisher, never()).publish(eq(EventType.EXPENSE_FLAGGED_FOR_REVIEW), any(), any(), any(), any());
    }

    @Test
    void execute_anomalousScore_flagsForReview() {
        Expense expense = expense(2L);
        when(expenseRepository.findById(2L)).thenReturn(Optional.of(expense));
        when(expenseRepository.findTop20ByUserIdOrderByCreatedAtDesc(20L)).thenReturn(Collections.emptyList());
        when(expenseRepository.findTop20ByUserIdAndCategoryOrderByExpenseDateDesc(20L, Expense.ExpenseCategory.MEALS))
                .thenReturn(Collections.emptyList());
        when(scoringClient.score(any(AnomalyScoreQuery.class)))
                .thenReturn(new AnomalyScoreResult(true, 0.81, true, 96.0, Map.of("duplicate_similarity", 0.9), "isolation-forest-v1"));

        ExpenseSagaContext context = new ExpenseSagaContext(2L, 6L, new BigDecimal("75.00"));
        step.execute(context);

        assertTrue(context.isFlaggedForReview());
        verify(eventPublisher).publish(eq(EventType.EXPENSE_FLAGGED_FOR_REVIEW), eq("Expense"), eq(2L), eq(6L), any());
    }

    @Test
    void execute_scoringServiceUnavailable_failsSafeToFlaggedForReview() {
        Expense expense = expense(3L);
        when(expenseRepository.findById(3L)).thenReturn(Optional.of(expense));
        when(expenseRepository.findTop20ByUserIdOrderByCreatedAtDesc(20L)).thenReturn(Collections.emptyList());
        when(expenseRepository.findTop20ByUserIdAndCategoryOrderByExpenseDateDesc(20L, Expense.ExpenseCategory.MEALS))
                .thenReturn(Collections.emptyList());
        when(scoringClient.score(any(AnomalyScoreQuery.class))).thenReturn(AnomalyScoreResult.unavailable());

        ExpenseSagaContext context = new ExpenseSagaContext(3L, 6L, new BigDecimal("75.00"));
        step.execute(context);

        // Circuit open / scoring down must never silently mean "assume
        // clean" -- it means "a human should look at this instead".
        assertTrue(context.isFlaggedForReview());

        ArgumentCaptor<ExpenseAnomalyAssessment> captor = ArgumentCaptor.forClass(ExpenseAnomalyAssessment.class);
        verify(assessmentRepository).save(captor.capture());
        assertTrue(captor.getValue().isScoringUnavailable());
    }

    @Test
    void compensate_isANoOp() {
        ExpenseSagaContext context = new ExpenseSagaContext(1L, 6L, BigDecimal.TEN);
        assertDoesNotThrow(() -> step.compensate(context));
        verifyNoInteractions(eventPublisher, assessmentRepository);
    }
}

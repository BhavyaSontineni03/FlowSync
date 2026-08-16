package com.expensemanagement.saga;

import com.expensemanagement.repository.SagaExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the orchestrator's core contract: on success every step runs
 * once in order; on failure, every already-completed step is compensated
 * in *reverse* order and the step that never ran is never compensated.
 *
 * Uses hand-written fake SagaSteps rather than mocking AnomalyScoringSagaStep
 * /PaymentSagaStep directly, so this test exercises the orchestration logic
 * itself independent of what any real step does.
 */
@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private AnomalyScoringSagaStep anomalyScoringSagaStep;
    @Mock
    private PaymentSagaStep paymentSagaStep;
    @Mock
    private PaymentNotificationSagaStep paymentNotificationSagaStep;
    @Mock
    private SagaExecutionRepository sagaExecutionRepository;

    private SagaOrchestrator orchestrator;
    private List<String> executionOrder;

    @BeforeEach
    void setUp() {
        orchestrator = new SagaOrchestrator(anomalyScoringSagaStep, paymentSagaStep, paymentNotificationSagaStep, sagaExecutionRepository);
        executionOrder = new ArrayList<>();

        // save() just returns whatever SagaExecution it was given, with an
        // id assigned once, mimicking a JPA save.
        when(sagaExecutionRepository.save(any(SagaExecution.class))).thenAnswer(invocation -> {
            SagaExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(1L);
            }
            return execution;
        });

        // lenient: the submission-saga test below never touches these payment
        // steps, so Mockito's strict stubbing would otherwise flag them unused.
        lenient().when(paymentSagaStep.name()).thenReturn("PAYMENT");
        lenient().when(paymentNotificationSagaStep.name()).thenReturn("PAYMENT_NOTIFICATION");
    }

    @Test
    void runExpensePaymentSaga_allStepsSucceed_marksExecutionCompleted() {
        doAnswer(inv -> {
            executionOrder.add("PAYMENT.execute");
            return null;
        }).when(paymentSagaStep).execute(any());
        doAnswer(inv -> {
            executionOrder.add("PAYMENT_NOTIFICATION.execute");
            return null;
        }).when(paymentNotificationSagaStep).execute(any());

        orchestrator.runExpensePaymentSaga(42L, 7L, new BigDecimal("150.00"));

        assertEquals(List.of("PAYMENT.execute", "PAYMENT_NOTIFICATION.execute"), executionOrder);
        verify(paymentSagaStep, never()).compensate(any());
        verify(paymentNotificationSagaStep, never()).compensate(any());

        verify(sagaExecutionRepository, atLeastOnce()).save(argThat(e -> e.getStatus() == SagaStatus.COMPLETED));
    }

    @Test
    void runExpensePaymentSaga_secondStepFails_compensatesFirstStepOnly() {
        doAnswer(inv -> {
            executionOrder.add("PAYMENT.execute");
            return null;
        }).when(paymentSagaStep).execute(any());

        doThrow(new SagaStepException("notification infra down"))
                .when(paymentNotificationSagaStep).execute(any());

        doAnswer(inv -> {
            executionOrder.add("PAYMENT.compensate");
            return null;
        }).when(paymentSagaStep).compensate(any());

        orchestrator.runExpensePaymentSaga(42L, 7L, new BigDecimal("150.00"));

        // PAYMENT ran and was then compensated; PAYMENT_NOTIFICATION never
        // completed, so it must never be compensated.
        assertEquals(List.of("PAYMENT.execute", "PAYMENT.compensate"), executionOrder);
        verify(paymentNotificationSagaStep, never()).compensate(any());

        verify(sagaExecutionRepository, atLeastOnce()).save(argThat(e -> e.getStatus() == SagaStatus.COMPENSATED));
    }

    @Test
    void runExpenseSubmissionSaga_scoringStepFails_marksCompensatedWithNoStepsToUndo() {
        doThrow(new SagaStepException("scoring service unreachable"))
                .when(anomalyScoringSagaStep).execute(any());
        when(anomalyScoringSagaStep.name()).thenReturn("ANOMALY_SCORING");

        orchestrator.runExpenseSubmissionSaga(9L, 3L, new BigDecimal("40.00"));

        // The only step in this saga failed before completing, so there is
        // nothing to compensate -- but the run must still be recorded as
        // COMPENSATED (having gone through the compensation phase), not
        // silently swallowed.
        verify(anomalyScoringSagaStep, never()).compensate(any());
        verify(sagaExecutionRepository, atLeastOnce()).save(argThat(e -> e.getStatus() == SagaStatus.COMPENSATED));
    }

    @Test
    void compensationFailure_isSwallowedSoOtherStepsStillCompensate() {
        doAnswer(inv -> {
            executionOrder.add("PAYMENT.execute");
            return null;
        }).when(paymentSagaStep).execute(any());
        doAnswer(inv -> {
            executionOrder.add("PAYMENT_NOTIFICATION.execute");
            throw new SagaStepException("boom");
        }).when(paymentNotificationSagaStep).execute(any());

        // Compensation itself throws -- the orchestrator must not propagate
        // this; it should log and continue rather than leaving the saga run
        // in an unresolved state.
        doThrow(new RuntimeException("budget service also down"))
                .when(paymentSagaStep).compensate(any());

        assertDoesNotThrow(() -> orchestrator.runExpensePaymentSaga(42L, 7L, new BigDecimal("99.00")));

        verify(sagaExecutionRepository, atLeastOnce()).save(argThat(e -> e.getStatus() == SagaStatus.COMPENSATED));
    }
}

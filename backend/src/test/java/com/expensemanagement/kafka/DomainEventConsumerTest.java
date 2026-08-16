package com.expensemanagement.kafka;

import com.expensemanagement.event.EventType;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.saga.SagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventConsumerTest {

    @Mock private SagaOrchestrator sagaOrchestrator;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private DomainEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DomainEventConsumer(sagaOrchestrator, expenseRepository, objectMapper);
    }

    private Expense expenseWithAmount(Long id, Long orgId, BigDecimal amount) {
        Organization org = Organization.builder().id(orgId).build();
        return Expense.builder().id(id).organization(org).amount(amount).expenseDate(LocalDate.now()).build();
    }

    private ConsumerRecord<String, String> recordFor(EventEnvelope envelope) throws Exception {
        return new ConsumerRecord<>("expense-events.org-" + envelope.orgId(), 0, 0L, String.valueOf(envelope.aggregateId()),
                objectMapper.writeValueAsString(envelope));
    }

    @Test
    void expenseSubmittedEvent_triggersSubmissionSaga() throws Exception {
        Expense expense = expenseWithAmount(10L, 3L, new BigDecimal("55.00"));
        when(expenseRepository.findById(10L)).thenReturn(Optional.of(expense));

        EventEnvelope envelope = new EventEnvelope(1L, EventType.EXPENSE_SUBMITTED, "Expense", 10L, 3L, LocalDateTime.now(), "{}");
        consumer.onEvent(recordFor(envelope), acknowledgment);

        verify(sagaOrchestrator).runExpenseSubmissionSaga(10L, 3L, new BigDecimal("55.00"));
        verify(sagaOrchestrator, never()).runExpensePaymentSaga(any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void expenseApprovedEvent_triggersPaymentSaga() throws Exception {
        Expense expense = expenseWithAmount(11L, 4L, new BigDecimal("310.00"));
        when(expenseRepository.findById(11L)).thenReturn(Optional.of(expense));

        EventEnvelope envelope = new EventEnvelope(2L, EventType.EXPENSE_APPROVED, "Expense", 11L, 4L, LocalDateTime.now(), "{}");
        consumer.onEvent(recordFor(envelope), acknowledgment);

        verify(sagaOrchestrator).runExpensePaymentSaga(11L, 4L, new BigDecimal("310.00"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void unhandledEventType_doesNotTriggerAnySaga_butStillAcknowledges() throws Exception {
        EventEnvelope envelope = new EventEnvelope(3L, EventType.EXPENSE_REJECTED, "Expense", 12L, 4L, LocalDateTime.now(), "{}");
        consumer.onEvent(recordFor(envelope), acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedMessage_isLoggedNotThrown_andStillAcknowledged() {
        ConsumerRecord<String, String> badRecord = new ConsumerRecord<>("expense-events.org-3", 0, 0L, "key", "not-json");

        consumer.onEvent(badRecord, acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(acknowledgment).acknowledge();
    }
}

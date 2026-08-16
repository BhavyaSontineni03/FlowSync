package com.expensemanagement.kafka;

import com.expensemanagement.event.EventType;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.saga.SagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Subscribes to every organization's event topic at once via a regex topic
 * pattern (spring-kafka resolves and tracks new matching topics without a
 * restart, so a newly-registered org's topic is picked up automatically) and
 * drives the saga orchestrator off what it sees.
 *
 * This is what makes the per-tenant topic isolation actually load-bearing
 * rather than cosmetic: this single consumer group processes every tenant's
 * events, but because each tenant's events live on their own topic (their
 * own set of partitions), one tenant's consumer lag -- a burst of
 * submissions, a slow downstream step -- only delays that tenant's own
 * events. A shared, single topic would mean one noisy org's backlog delays
 * every other org's saga processing too.
 */
@Component
@RequiredArgsConstructor
@Profile("!loadtest")
@Slf4j
public class DomainEventConsumer {

    private final SagaOrchestrator sagaOrchestrator;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topicPattern = "${app.events.topic-prefix}\\..*", groupId = "expense-saga-orchestrator")
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
            dispatch(envelope);
        } catch (Exception e) {
            // Deliberately does not rethrow: a poison message should not
            // block the partition forever. It's logged for investigation;
            // the outbox relay and this consumer's own idempotent handling
            // mean a transient failure here does not lose the event, since
            // the domain_events table still has it.
            log.error("Failed to process event from {} at offset {}: {}", record.topic(), record.offset(), e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private void dispatch(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case EXPENSE_SUBMITTED -> expenseRepository.findById(envelope.aggregateId()).ifPresent(expense ->
                    sagaOrchestrator.runExpenseSubmissionSaga(expense.getId(), envelope.orgId(), expense.getAmount()));

            case EXPENSE_APPROVED -> expenseRepository.findById(envelope.aggregateId()).ifPresent(expense ->
                    sagaOrchestrator.runExpensePaymentSaga(expense.getId(), envelope.orgId(), expense.getAmount()));

            default -> log.debug("No saga trigger registered for event type {}", envelope.eventType());
        }
    }
}

package com.expensemanagement.event;

import com.expensemanagement.kafka.DomainEventProducer;
import com.expensemanagement.repository.DomainEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional outbox: the single entry point every service uses to raise a
 * domain event.
 *
 * The event row is written to domain_events inside the *caller's* database
 * transaction -- the same one that changes the Expense/Approval/etc. row the
 * event describes. Kafka publication is deferred until that transaction
 * actually commits (via a TransactionSynchronization registered on the
 * current transaction). This closes the classic dual-write gap: without it,
 * a service could save its entity, then crash or fail before publishing to
 * Kafka, leaving state and event stream disagreeing forever -- or publish an
 * event whose transaction then rolls back, telling downstream consumers
 * about something that never actually happened.
 *
 * Must be called from within an existing @Transactional method; that's
 * enforced by MANDATORY propagation rather than silently starting a new
 * transaction, because publishing outside of the business transaction would
 * defeat the whole point.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final DomainEventRepository domainEventRepository;
    private final DomainEventProducer domainEventProducer;
    private final EventPublicationTracker publicationTracker;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public DomainEvent publish(EventType eventType, String aggregateType, Long aggregateId, Long orgId, Object payload) {
        DomainEvent event = domainEventRepository.save(DomainEvent.builder()
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .orgId(orgId)
                .payload(serialize(payload))
                .published(false)
                .build());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishNow(event);
                }
            });
        } else {
            // No active synchronization (e.g. called from a test without a
            // transactional context) -- publish immediately as a fallback.
            publishNow(event);
        }

        return event;
    }

    void publishNow(DomainEvent event) {
        domainEventProducer.send(event)
                .thenAccept(result -> {
                    // Only mark published after a real broker send. A null
                    // result (or failed future) must leave the outbox row
                    // unpublished so sagas are not silently pretended to run.
                    if (result != null) {
                        publicationTracker.markPublished(event.getId());
                    } else {
                        log.warn("Event {} (#{}) send returned no result; leaving unpublished",
                                event.getEventType(), event.getId());
                    }
                })
                .exceptionally(ex -> {
                    log.warn("Event {} (#{}) will be retried by the outbox relay: {}",
                            event.getEventType(), event.getId(), ex.getMessage());
                    return null;
                });
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}

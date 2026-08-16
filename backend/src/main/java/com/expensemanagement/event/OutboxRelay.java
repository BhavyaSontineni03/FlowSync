package com.expensemanagement.event;

import com.expensemanagement.kafka.DomainEventProducer;
import com.expensemanagement.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Catches the one gap the transactional outbox otherwise leaves: a process
 * crash between the business transaction committing and the deferred Kafka
 * send completing. Every 30 seconds, anything still marked unpublished after
 * a 10-second grace period (long enough that we're not just racing the
 * normal async send) gets a resend attempt. Idempotent by design -- the
 * event's own id is used as the Kafka message key's tiebreaker via the
 * aggregateId, and consumers are expected to handle at-least-once delivery
 * the same way any Kafka consumer must.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !loadtest")
public class OutboxRelay {

    private final DomainEventRepository domainEventRepository;
    private final DomainEventProducer domainEventProducer;
    private final EventPublicationTracker publicationTracker;

    @Scheduled(fixedDelayString = "${app.events.outbox-relay-interval-ms:30000}")
    public void relayStaleEvents() {
        var cutoff = LocalDateTime.now().minusSeconds(10);
        var stale = domainEventRepository.findStalePendingEvents(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Outbox relay resending {} event(s) not yet confirmed published", stale.size());
        for (DomainEvent event : stale) {
            domainEventProducer.send(event)
                    .thenAccept(result -> {
                        if (result != null) {
                            publicationTracker.markPublished(event.getId());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Outbox relay retry failed for event #{}: {}", event.getId(), ex.getMessage());
                        return null;
                    });
        }
    }
}

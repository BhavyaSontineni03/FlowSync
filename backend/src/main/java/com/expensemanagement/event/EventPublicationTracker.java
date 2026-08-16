package com.expensemanagement.event;

import com.expensemanagement.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks an outbox row as published in its own, independent transaction.
 * Kept as a separate bean (rather than a method on EventPublisher) because
 * Spring's proxy-based @Transactional only intercepts calls that go through
 * the bean's proxy -- a method on the same class calling itself would
 * silently skip REQUIRES_NEW and run in whatever transaction (or lack of
 * one) was already active.
 */
@Component
@RequiredArgsConstructor
public class EventPublicationTracker {

    private final DomainEventRepository domainEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long eventId) {
        domainEventRepository.findById(eventId).ifPresent(event -> {
            event.setPublished(true);
            domainEventRepository.save(event);
        });
    }
}

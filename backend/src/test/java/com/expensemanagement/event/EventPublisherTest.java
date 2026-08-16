package com.expensemanagement.event;

import com.expensemanagement.kafka.DomainEventProducer;
import com.expensemanagement.repository.DomainEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Outside of a real Spring-managed transaction,
 * TransactionSynchronizationManager.isSynchronizationActive() is false, so
 * these tests exercise EventPublisher's immediate-publish fallback path.
 * The deferred, after-commit path is exercised indirectly by the
 * @SpringBootTest integration tests that call through ExpenseService /
 * ApprovalService inside a real @Transactional method.
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock private DomainEventRepository domainEventRepository;
    @Mock private DomainEventProducer domainEventProducer;
    @Mock private EventPublicationTracker publicationTracker;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(domainEventRepository, domainEventProducer, publicationTracker, new ObjectMapper());
    }

    @Test
    void publish_savesEventRowWithSerializedPayload() {
        when(domainEventRepository.save(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(domainEventProducer.send(any())).thenReturn(CompletableFuture.completedFuture(sendResult));

        DomainEvent event = eventPublisher.publish(
                EventType.EXPENSE_SUBMITTED, "Expense", 10L, 3L, Map.of("amount", "99.50"));

        assertEquals(EventType.EXPENSE_SUBMITTED, event.getEventType());
        assertEquals("Expense", event.getAggregateType());
        assertEquals(10L, event.getAggregateId());
        assertEquals(3L, event.getOrgId());
        assertTrue(event.getPayload().contains("99.50"));
    }

    @Test
    void publish_withNoActiveTransaction_publishesImmediatelyAndMarksPublished() {
        when(domainEventRepository.save(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(domainEventProducer.send(any())).thenReturn(CompletableFuture.completedFuture(sendResult));

        eventPublisher.publish(EventType.EXPENSE_APPROVED, "Expense", 11L, 4L, Map.of());

        verify(domainEventProducer).send(argThat(e -> e.getId().equals(7L)));
        verify(publicationTracker).markPublished(7L);
    }

    @Test
    void publish_whenKafkaSendFails_doesNotMarkPublished_leavingItForTheOutboxRelay() {
        when(domainEventRepository.save(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent e = inv.getArgument(0);
            e.setId(8L);
            return e;
        });
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(domainEventProducer.send(any())).thenReturn(failed);

        eventPublisher.publish(EventType.EXPENSE_PAID, "Expense", 12L, 4L, Map.of());

        verify(publicationTracker, never()).markPublished(any());
    }

    @Test
    void publish_whenKafkaTemplateAbsent_doesNotMarkPublished() {
        when(domainEventRepository.save(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent e = inv.getArgument(0);
            e.setId(9L);
            return e;
        });
        when(domainEventProducer.send(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("KafkaTemplate unavailable")));

        eventPublisher.publish(EventType.EXPENSE_SUBMITTED, "Expense", 13L, 5L, Map.of());

        verify(publicationTracker, never()).markPublished(any());
    }

    @Test
    void publish_whenSendResultNull_doesNotMarkPublished() {
        when(domainEventRepository.save(any(DomainEvent.class))).thenAnswer(inv -> {
            DomainEvent e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });
        when(domainEventProducer.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        eventPublisher.publish(EventType.EXPENSE_SUBMITTED, "Expense", 14L, 5L, Map.of());

        verify(publicationTracker, never()).markPublished(any());
    }
}

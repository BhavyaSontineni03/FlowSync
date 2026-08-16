package com.expensemanagement.kafka;

import com.expensemanagement.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * The only class that talks to KafkaTemplate directly.
 * Everything else goes through EventPublisher, which owns the
 * transactional-outbox guarantee. This class only sends.
 *
 * KafkaTemplate is optional so the "loadtest" profile can exclude Kafka
 * and still exercise the sync HTTP write path. When the template
 * is absent, send fails so callers do not mark the outbox row published.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventProducer {

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate;
    private final EventTopicResolver topicResolver;
    private final ObjectMapper objectMapper;

    public boolean isAvailable() {
        return kafkaTemplate.getIfAvailable() != null;
    }

    public CompletableFuture<SendResult<String, String>> send(DomainEvent event) {
        KafkaTemplate<String, String> template = kafkaTemplate.getIfAvailable();
        if (template == null) {
            log.debug("KafkaTemplate absent; leaving event {} ({}#{}) unpublished",
                    event.getId(), event.getAggregateType(), event.getAggregateId());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("KafkaTemplate unavailable; event left unpublished"));
        }

        String topic = topicResolver.topicFor(event.getOrgId());
        String key = String.valueOf(event.getAggregateId());
        String body = serialize(event);

        return template.send(topic, key, body)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for {}#{} to {}: {}",
                                event.getEventType(), event.getAggregateType(), event.getAggregateId(), topic, ex.getMessage());
                    } else {
                        log.debug("Published {} for {}#{} to {} partition {} offset {}",
                                event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                                topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }

    private String serialize(DomainEvent event) {
        try {
            EventEnvelope envelope = new EventEnvelope(
                    event.getId(), event.getEventType(), event.getAggregateType(),
                    event.getAggregateId(), event.getOrgId(), event.getOccurredAt(), event.getPayload());
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event envelope for event " + event.getId(), e);
        }
    }
}

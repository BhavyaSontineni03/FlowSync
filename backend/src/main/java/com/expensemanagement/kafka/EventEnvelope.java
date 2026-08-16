package com.expensemanagement.kafka;

import com.expensemanagement.event.EventType;

import java.time.LocalDateTime;

/**
 * Self-describing wrapper actually put on the wire. The domain_events table
 * stores just the payload (it already has typed columns for everything
 * else); the Kafka message needs to carry that same metadata inline since a
 * consumer reading from a per-org topic has nothing else to join against.
 */
public record EventEnvelope(
        Long eventId,
        EventType eventType,
        String aggregateType,
        Long aggregateId,
        Long orgId,
        LocalDateTime occurredAt,
        String payload
) {}

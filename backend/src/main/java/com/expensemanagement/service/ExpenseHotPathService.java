package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.event.DomainEvent;
import com.expensemanagement.event.EventPublicationTracker;
import com.expensemanagement.event.EventType;
import com.expensemanagement.event.ExpenseSubmittedEvent;
import com.expensemanagement.kafka.DomainEventProducer;
import com.expensemanagement.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JDBC hot path for expense create + submit. Avoids Hibernate session/flush/lazy
 * graph cost on the measured sync write path. Saga/Kafka/ML stay off this path:
 * submit writes an outbox row in the same TX and schedules Kafka publish
 * AFTER_COMMIT; side effects (notify/activity/ws) run via
 * {@link ExpenseSubmittedEvent} AFTER_COMMIT (disabled under loadtest profile).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseHotPathService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DomainEventProducer domainEventProducer;
    private final EventPublicationTracker publicationTracker;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public ExpenseDto createFast(ExpenseDto expenseDto, Long userId, Long organizationId) {
        if (expenseDto.getAmount() == null || expenseDto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Expense amount must be positive");
        }
        if (expenseDto.getDescription() == null || expenseDto.getDescription().isBlank()) {
            throw new RuntimeException("Description is required");
        }
        if (expenseDto.getExpenseDate() == null) {
            throw new RuntimeException("Expense date is required");
        }
        if (expenseDto.getCategory() == null) {
            throw new RuntimeException("Category is required");
        }

        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("description", expenseDto.getDescription())
                .addValue("amount", expenseDto.getAmount())
                .addValue("expenseDate", expenseDto.getExpenseDate())
                .addValue("category", expenseDto.getCategory().name())
                .addValue("notes", expenseDto.getNotes())
                .addValue("userId", userId)
                .addValue("orgId", organizationId)
                .addValue("now", Timestamp.valueOf(now));

        // Single round-trip: insert only if caller belongs to org (no prior SELECT).
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbc.update("""
                INSERT INTO expenses (
                    description, amount, expense_date, category, status, notes,
                    user_id, organization_id, created_at, updated_at
                )
                SELECT :description, :amount, :expenseDate, :category, 'PENDING', :notes,
                       :userId, :orgId, :now, :now
                WHERE EXISTS (
                    SELECT 1 FROM users
                    WHERE id = :userId AND organization_id = :orgId AND enabled = true
                )
                """, params, keyHolder, new String[]{"id"});
        if (inserted != 1) {
            throw new RuntimeException("User not found");
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain expense id after insert");
        }
        long expenseId = key.longValue();

        return ExpenseDto.builder()
                .id(expenseId)
                .description(expenseDto.getDescription())
                .amount(expenseDto.getAmount())
                .expenseDate(expenseDto.getExpenseDate())
                .category(expenseDto.getCategory())
                .status(Expense.ExpenseStatus.PENDING)
                .notes(expenseDto.getNotes())
                .userId(userId)
                .organizationId(organizationId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Transactional
    public ExpenseDto submitFast(Long expenseId, Long userId, Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", expenseId)
                .addValue("userId", userId)
                .addValue("orgId", organizationId)
                .addValue("now", Timestamp.valueOf(now));

        // One round-trip: authorize + status flip, returning columns needed for outbox/DTO.
        List<Map<String, Object>> updatedRows = jdbc.queryForList("""
                UPDATE expenses e
                SET status = 'SUBMITTED', submitted_at = :now, updated_at = :now
                FROM users u
                WHERE e.id = :id
                  AND e.user_id = :userId
                  AND e.organization_id = :orgId
                  AND e.status = 'PENDING'
                  AND u.id = e.user_id
                  AND u.manager_id IS NOT NULL
                RETURNING e.id, e.amount, e.category, e.description, e.expense_date,
                          e.notes, e.user_id, e.organization_id, e.created_at
                """, params);

        if (updatedRows.isEmpty()) {
            diagnoseSubmitFailure(expenseId, userId, organizationId);
        }

        Map<String, Object> row = updatedRows.get(0);
        Long expenseOrgId = ((Number) row.get("organization_id")).longValue();
        Long expenseUserId = ((Number) row.get("user_id")).longValue();
        BigDecimal amount = (BigDecimal) row.get("amount");
        String category = String.valueOf(row.get("category"));
        String payload = serializePayload(Map.of(
                "amount", amount.toPlainString(),
                "category", category));

        KeyHolder eventKeys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO domain_events (
                    event_type, aggregate_type, aggregate_id, org_id, payload, published, occurred_at
                ) VALUES (
                    'EXPENSE_SUBMITTED', 'Expense', :aggregateId, :orgId, :payload, false, :now
                )
                """, new MapSqlParameterSource()
                .addValue("aggregateId", expenseId)
                .addValue("orgId", expenseOrgId)
                .addValue("payload", payload)
                .addValue("now", Timestamp.valueOf(now)), eventKeys, new String[]{"id"});

        Number eventKey = eventKeys.getKey();
        if (eventKey == null) {
            throw new IllegalStateException("Failed to obtain domain_event id after insert");
        }

        DomainEvent event = DomainEvent.builder()
                .id(eventKey.longValue())
                .eventType(EventType.EXPENSE_SUBMITTED)
                .aggregateType("Expense")
                .aggregateId(expenseId)
                .orgId(expenseOrgId)
                .payload(payload)
                .published(false)
                .occurredAt(now)
                .build();
        scheduleAfterCommitPublish(event);

        applicationEventPublisher.publishEvent(new ExpenseSubmittedEvent(expenseId, expenseOrgId));

        return ExpenseDto.builder()
                .id(expenseId)
                .description(String.valueOf(row.get("description")))
                .amount(amount)
                .expenseDate(toLocalDate(row.get("expense_date")))
                .category(Expense.ExpenseCategory.valueOf(category))
                .status(Expense.ExpenseStatus.SUBMITTED)
                .notes(row.get("notes") != null ? String.valueOf(row.get("notes")) : null)
                .userId(expenseUserId)
                .organizationId(expenseOrgId)
                .createdAt(toLocalDateTime(row.get("created_at")))
                .updatedAt(now)
                .build();
    }

    private void diagnoseSubmitFailure(Long expenseId, Long userId, Long organizationId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT e.status, e.user_id, e.organization_id, u.manager_id
                    FROM expenses e
                    JOIN users u ON u.id = e.user_id
                    WHERE e.id = :id
                    """, Map.of("id", expenseId));
        } catch (EmptyResultDataAccessException ex) {
            throw new RuntimeException("Expense not found");
        }

        Long expenseOrgId = ((Number) row.get("organization_id")).longValue();
        Long expenseUserId = ((Number) row.get("user_id")).longValue();
        if (!expenseOrgId.equals(organizationId)) {
            throw new RuntimeException("Expense does not belong to this organization");
        }
        if (!expenseUserId.equals(userId)) {
            throw new RuntimeException("Not authorized to submit this expense");
        }
        if (!"PENDING".equals(String.valueOf(row.get("status")))) {
            throw new RuntimeException("Expense is already submitted");
        }
        if (row.get("manager_id") == null) {
            throw new RuntimeException(
                    "Cannot submit expense: You do not have a manager assigned. Please contact HR to assign a manager first.");
        }
        throw new RuntimeException("Expense is already submitted");
    }

    private void scheduleAfterCommitPublish(DomainEvent event) {
        // Under loadtest Kafka is off. Keep the outbox row, skip after-commit
        // publish attempts (and the WARN noise they would produce).
        if (!domainEventProducer.isAvailable()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishNow(event);
                }
            });
        } else {
            publishNow(event);
        }
    }

    private void publishNow(DomainEvent event) {
        domainEventProducer.send(event)
                .thenAccept(result -> {
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

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}

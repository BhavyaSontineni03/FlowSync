package com.expensemanagement.repository;

import com.expensemanagement.event.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    List<DomainEvent> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, Long aggregateId);

    /**
     * Events that committed to the database but were never confirmed as
     * published to Kafka -- either the after-commit hook hasn't run yet
     * (still within the same request) or the process crashed between commit
     * and publish. The outbox relay uses this to catch up.
     */
    @Query("SELECT e FROM DomainEvent e WHERE e.published = false AND e.occurredAt < :cutoff ORDER BY e.occurredAt ASC")
    List<DomainEvent> findStalePendingEvents(@Param("cutoff") LocalDateTime cutoff);
}

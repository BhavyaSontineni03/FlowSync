package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.event.EventPublicationTracker;
import com.expensemanagement.kafka.DomainEventProducer;
import com.expensemanagement.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseHotPathServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private DomainEventProducer domainEventProducer;

    @Mock
    private EventPublicationTracker publicationTracker;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ExpenseHotPathService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseHotPathService(
                jdbc, new ObjectMapper(), domainEventProducer, publicationTracker, applicationEventPublisher);
    }

    @Test
    void createFast_insertsPendingExpenseAndReturnsDto() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(2);
                    keyHolder.getKeyList().add(Map.of("id", 42L));
                    return 1;
                });

        ExpenseDto dto = ExpenseDto.builder()
                .description("Taxi")
                .amount(new BigDecimal("25.50"))
                .expenseDate(LocalDate.of(2026, 7, 24))
                .category(Expense.ExpenseCategory.TRANSPORTATION)
                .build();

        ExpenseDto created = service.createFast(dto, 7L, 3L);

        assertEquals(42L, created.getId());
        assertEquals(Expense.ExpenseStatus.PENDING, created.getStatus());
        assertEquals(7L, created.getUserId());
        assertEquals(3L, created.getOrganizationId());
        verify(applicationEventPublisher, never()).publishEvent(any());
        verify(domainEventProducer, never()).send(any());
    }

    @Test
    void createFast_rejectsNonPositiveAmount() {
        ExpenseDto dto = ExpenseDto.builder()
                .description("Bad")
                .amount(BigDecimal.ZERO)
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.MEALS)
                .build();

        assertThrows(RuntimeException.class, () -> service.createFast(dto, 1L, 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void submitFast_updatesStatusWritesOutboxAndFiresEvent() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 42L);
        row.put("amount", new BigDecimal("25.50"));
        row.put("category", "TRANSPORTATION");
        row.put("description", "Taxi");
        row.put("expense_date", LocalDate.of(2026, 7, 24));
        row.put("notes", null);
        row.put("user_id", 7L);
        row.put("organization_id", 3L);
        row.put("created_at", null);

        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(2);
                    keyHolder.getKeyList().add(Map.of("id", 99L));
                    return 1;
                });
        when(domainEventProducer.isAvailable()).thenReturn(false);

        ExpenseDto submitted = service.submitFast(42L, 7L, 3L);

        assertEquals(Expense.ExpenseStatus.SUBMITTED, submitted.getStatus());
        assertEquals(42L, submitted.getId());
        verify(applicationEventPublisher).publishEvent(any(com.expensemanagement.event.ExpenseSubmittedEvent.class));
        verify(domainEventProducer, never()).send(any());
    }

    @Test
    void submitFast_rejectsMissingExpense() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), anyMap())).thenThrow(new EmptyResultDataAccessException(1));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitFast(1L, 1L, 1L));
        assertTrue(ex.getMessage().contains("not found"));
    }
}

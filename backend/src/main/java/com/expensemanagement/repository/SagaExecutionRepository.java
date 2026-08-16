package com.expensemanagement.repository;

import com.expensemanagement.saga.SagaExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaExecutionRepository extends JpaRepository<SagaExecution, Long> {
    List<SagaExecution> findByExpenseIdOrderByStartedAtDesc(Long expenseId);
}

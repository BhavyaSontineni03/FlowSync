package com.expensemanagement.repository;

import com.expensemanagement.model.ExpenseAnomalyAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpenseAnomalyAssessmentRepository extends JpaRepository<ExpenseAnomalyAssessment, Long> {
    Optional<ExpenseAnomalyAssessment> findByExpenseId(Long expenseId);
}

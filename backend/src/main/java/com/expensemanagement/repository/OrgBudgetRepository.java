package com.expensemanagement.repository;

import com.expensemanagement.model.OrgBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrgBudgetRepository extends JpaRepository<OrgBudget, Long> {
    Optional<OrgBudget> findByOrganizationIdAndPeriodYearAndPeriodMonth(Long organizationId, Integer periodYear, Integer periodMonth);
}

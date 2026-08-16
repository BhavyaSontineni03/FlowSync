package com.expensemanagement.repository;

import com.expensemanagement.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * Loads an expense with associations needed by post-commit submit side
     * effects (activity log, manager notify). Without the fetch, an async
     * AFTER_COMMIT listener hitting lazy user/organization/manager outside a
     * session throws LazyInitializationException and silently skips notify.
     */
    @Query("SELECT e FROM Expense e "
            + "JOIN FETCH e.user u "
            + "JOIN FETCH e.organization "
            + "LEFT JOIN FETCH u.manager "
            + "WHERE e.id = :id")
    Optional<Expense> findByIdWithUserOrgAndManager(@Param("id") Long id);

    Page<Expense> findByOrganizationId(Long organizationId, Pageable pageable);
    
    Page<Expense> findByUserId(Long userId, Pageable pageable);
    
    Page<Expense> findByOrganizationIdAndStatus(Long organizationId, Expense.ExpenseStatus status, Pageable pageable);
    
    List<Expense> findByOrganizationIdAndStatus(Long organizationId, Expense.ExpenseStatus status);
    
    @Query("SELECT e FROM Expense e WHERE e.organization.id = :orgId AND e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findByOrganizationIdAndDateRange(
        @Param("orgId") Long orgId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.organization.id = :orgId AND e.status = :status")
    java.math.BigDecimal getTotalAmountByOrganizationAndStatus(
        @Param("orgId") Long orgId,
        @Param("status") Expense.ExpenseStatus status
    );
    
    @Query("SELECT e.category, SUM(e.amount) FROM Expense e WHERE e.organization.id = :orgId AND e.expenseDate BETWEEN :startDate AND :endDate GROUP BY e.category")
    List<Object[]> getExpensesByCategory(
        @Param("orgId") Long orgId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    // Advanced search with full-text search
    @Query("SELECT e FROM Expense e WHERE e.organization.id = :orgId " +
           "AND (:searchTerm IS NULL OR " +
           "LOWER(e.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(CAST(e.amount AS string)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND (:category IS NULL OR e.category = :category) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:startDate IS NULL OR e.expenseDate >= :startDate) " +
           "AND (:endDate IS NULL OR e.expenseDate <= :endDate)")
    Page<Expense> searchExpenses(
        @Param("orgId") Long orgId,
        @Param("searchTerm") String searchTerm,
        @Param("category") Expense.ExpenseCategory category,
        @Param("status") Expense.ExpenseStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
    
    /**
     * Find submitted expenses for employees where the given user is their assigned manager.
     * Only returns expenses that the manager can approve.
     */
    List<Expense> findByUserManagerIdAndStatusOrderByExpenseDateDesc(Long managerId, Expense.ExpenseStatus status);
    
    /**
     * Find all expenses for employees where the given user is their assigned manager.
     * Used for managers to view all expenses from their direct reports.
     */
    @Query("SELECT e FROM Expense e WHERE e.user.manager.id = :managerId")
    Page<Expense> findByUserManagerId(@Param("managerId") Long managerId, Pageable pageable);
    
    /**
     * Count expenses by manager and status - for manager stats dashboard
     */
    long countByUserManagerIdAndStatus(Long managerId, Expense.ExpenseStatus status);
    
    /**
     * Sum of approved expense amounts for manager's team
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user.manager.id = :managerId AND e.status = :status")
    java.math.BigDecimal sumAmountByUserManagerIdAndStatus(@Param("managerId") Long managerId, @Param("status") Expense.ExpenseStatus status);

    /**
     * A user's own recent submissions across all categories, most recent
     * first. Feeds the anomaly-scoring saga step's duplicate-similarity and
     * submission-velocity features -- deliberately scoped to one user, never
     * joined across users, so the scoring path can never leak one person's
     * submission history into another's request even inside the same org.
     */
    List<Expense> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * A user's prior amounts in one category, for the personalized
     * amount-zscore feature.
     */
    List<Expense> findTop20ByUserIdAndCategoryOrderByExpenseDateDesc(Long userId, Expense.ExpenseCategory category);
}


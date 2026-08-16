package com.expensemanagement.repository;

import com.expensemanagement.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByExpenseId(Long expenseId);
    
    @Query("SELECT a FROM Approval a WHERE a.approver.id = :approverId AND a.status = 'PENDING' AND a.expense.status = 'SUBMITTED'")
    List<Approval> findPendingApprovalsByApproverId(@Param("approverId") Long approverId);
    
    // Returns all approvals for an expense by approver (handles duplicates)
    List<Approval> findByExpenseIdAndApproverId(Long expenseId, Long approverId);
    
    // Returns the first approval for an expense by approver (most recent)
    Optional<Approval> findFirstByExpenseIdAndApproverIdOrderByCreatedAtDesc(Long expenseId, Long approverId);
}


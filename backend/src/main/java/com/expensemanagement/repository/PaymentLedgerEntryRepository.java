package com.expensemanagement.repository;

import com.expensemanagement.model.PaymentLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentLedgerEntryRepository extends JpaRepository<PaymentLedgerEntry, Long> {
    List<PaymentLedgerEntry> findByExpenseId(Long expenseId);
    Optional<PaymentLedgerEntry> findFirstByExpenseIdAndStatusOrderByCreatedAtDesc(Long expenseId, PaymentLedgerEntry.LedgerStatus status);
}

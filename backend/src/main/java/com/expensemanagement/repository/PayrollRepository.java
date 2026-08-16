package com.expensemanagement.repository;

import com.expensemanagement.model.Payroll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    Optional<Payroll> findByUserIdAndPeriodMonthAndPeriodYear(Long userId, Integer month, Integer year);
    
    Page<Payroll> findByUserId(Long userId, Pageable pageable);
    
    Page<Payroll> findByOrganizationId(Long organizationId, Pageable pageable);
    
    List<Payroll> findByOrganizationIdAndPeriodMonthAndPeriodYear(Long organizationId, Integer month, Integer year);
    
    @Query("SELECT p FROM Payroll p WHERE p.organization.id = :orgId AND p.periodYear = :year ORDER BY p.periodMonth DESC")
    List<Payroll> findByOrganizationAndYear(@Param("orgId") Long organizationId, @Param("year") Integer year);
    
    Page<Payroll> findByOrganizationIdAndStatus(Long organizationId, Payroll.PayrollStatus status, Pageable pageable);
}

package com.expensemanagement.repository;

import com.expensemanagement.model.Timesheet;
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
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
    Page<Timesheet> findByUserId(Long userId, Pageable pageable);
    
    Page<Timesheet> findByOrganizationId(Long organizationId, Pageable pageable);
    
    List<Timesheet> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    
    List<Timesheet> findByUserIdAndDateBetweenAndStatus(Long userId, LocalDate startDate, LocalDate endDate, Timesheet.TimesheetStatus status);
    
    Optional<Timesheet> findByUserIdAndDate(Long userId, LocalDate date);
    
    @Query("SELECT t FROM Timesheet t WHERE t.organization.id = :orgId AND t.status = :status")
    List<Timesheet> findPendingByOrganization(@Param("orgId") Long organizationId, @Param("status") Timesheet.TimesheetStatus status);
    
    @Query("SELECT t FROM Timesheet t WHERE t.user.id = :userId AND t.date >= :startDate AND t.date <= :endDate AND t.status = 'APPROVED'")
    List<Timesheet> findApprovedTimesheetsInRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Count only WORK entries (not leave) for payroll calculation
    @Query("SELECT COUNT(DISTINCT t.date) FROM Timesheet t WHERE t.user.id = :userId AND t.date >= :startDate AND t.date <= :endDate AND t.status = 'APPROVED' AND t.entryType = 'WORK'")
    Integer countApprovedDaysInRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Find timesheet entries by leave request ID (for leave-related entries)
    List<Timesheet> findByLeaveRequestId(Long leaveRequestId);
    
    // Check if a leave entry exists for a specific date
    @Query("SELECT t FROM Timesheet t WHERE t.user.id = :userId AND t.date = :date AND t.entryType = 'LEAVE'")
    Optional<Timesheet> findLeaveEntryByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    
    // Get all entries for a user in a date range (for weekly view)
    @Query("SELECT t FROM Timesheet t WHERE t.user.id = :userId AND t.date >= :startDate AND t.date <= :endDate ORDER BY t.date ASC")
    List<Timesheet> findByUserIdAndDateRangeOrdered(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Count leave days for payroll
    @Query("SELECT COUNT(DISTINCT t.date) FROM Timesheet t WHERE t.user.id = :userId AND t.date >= :startDate AND t.date <= :endDate AND t.entryType = 'LEAVE' AND t.isPaidLeave = true")
    Integer countPaidLeaveDaysInRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COUNT(DISTINCT t.date) FROM Timesheet t WHERE t.user.id = :userId AND t.date >= :startDate AND t.date <= :endDate AND t.entryType = 'LEAVE' AND t.isPaidLeave = false")
    Integer countUnpaidLeaveDaysInRange(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    /**
     * Find submitted timesheets for employees where the given user is their assigned manager.
     * Only returns timesheets that the manager can approve.
     */
    List<Timesheet> findByUserManagerIdAndStatusOrderByDateDesc(Long managerId, Timesheet.TimesheetStatus status);
    
    /**
     * Find all timesheets for employees where the given user is their assigned manager.
     * Used for managers to view all timesheets from their direct reports.
     */
    @Query("SELECT t FROM Timesheet t WHERE t.user.manager.id = :managerId")
    Page<Timesheet> findByUserManagerId(@Param("managerId") Long managerId, Pageable pageable);
    
    /**
     * Count timesheets by manager and status - for manager stats dashboard
     */
    long countByUserManagerIdAndStatus(Long managerId, Timesheet.TimesheetStatus status);
    
    /**
     * Sum of approved timesheet hours for manager's team
     */
    @Query("SELECT COALESCE(SUM(t.hours), 0) FROM Timesheet t WHERE t.user.manager.id = :managerId AND t.status = :status")
    Double sumHoursByUserManagerIdAndStatus(@Param("managerId") Long managerId, @Param("status") Timesheet.TimesheetStatus status);
}

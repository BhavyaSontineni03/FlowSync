package com.expensemanagement.repository;

import com.expensemanagement.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for leave request operations.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    
    Page<LeaveRequest> findByOrganizationId(Long organizationId, Pageable pageable);
    
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);
    
    Page<LeaveRequest> findByOrganizationIdAndStatus(Long organizationId, LeaveRequest.LeaveStatus status, Pageable pageable);
    
    List<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.LeaveStatus status);
    
    @Query("SELECT l FROM LeaveRequest l WHERE l.organization.id = :orgId " +
           "AND l.status = :status " +
           "AND (:startDate IS NULL OR l.endDate >= :startDate) " +
           "AND (:endDate IS NULL OR l.startDate <= :endDate)")
    List<LeaveRequest> findPendingByDateRange(
        @Param("orgId") Long orgId,
        @Param("status") LeaveRequest.LeaveStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId " +
           "AND ((l.startDate <= :endDate AND l.endDate >= :startDate)) " +
           "AND l.status = 'APPROVED'")
    List<LeaveRequest> findOverlappingApprovedLeaves(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    /**
     * Find overlapping leave requests that are either PENDING or APPROVED.
     * Used to prevent duplicate leave submissions.
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId " +
           "AND ((l.startDate <= :endDate AND l.endDate >= :startDate)) " +
           "AND l.status IN ('PENDING', 'APPROVED')")
    List<LeaveRequest> findOverlappingPendingOrApprovedLeaves(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT SUM(l.numberOfDays) FROM LeaveRequest l WHERE l.user.id = :userId " +
           "AND l.leaveType = :leaveType " +
           "AND l.status = 'APPROVED' " +
           "AND YEAR(l.startDate) = :year")
    Integer getTotalApprovedDaysByTypeAndYear(
        @Param("userId") Long userId,
        @Param("leaveType") LeaveRequest.LeaveType leaveType,
        @Param("year") int year
    );
    
    @Query("SELECT l FROM LeaveRequest l WHERE l.organization.id = :orgId " +
           "AND l.startDate >= :startDate " +
           "AND l.startDate <= :endDate")
    List<LeaveRequest> findByOrganizationIdAndDateRange(
        @Param("orgId") Long orgId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    /**
     * Find pending leave requests for employees where the given user is their assigned manager.
     * Only returns requests that the manager can approve.
     */
    List<LeaveRequest> findByUserManagerIdAndStatusOrderByCreatedAtDesc(Long managerId, LeaveRequest.LeaveStatus status);
    
    /**
     * Find all leave requests for employees where the given user is their assigned manager.
     * Used for managers to view all leave requests from their direct reports.
     */
    @Query("SELECT l FROM LeaveRequest l WHERE l.user.manager.id = :managerId")
    Page<LeaveRequest> findByUserManagerId(@Param("managerId") Long managerId, Pageable pageable);
    
    /**
     * Count leave requests by manager and status - for manager stats dashboard
     */
    long countByUserManagerIdAndStatus(Long managerId, LeaveRequest.LeaveStatus status);
    
    /**
     * Sum of approved leave days for manager's team
     */
    @Query("SELECT COALESCE(SUM(l.numberOfDays), 0) FROM LeaveRequest l WHERE l.user.manager.id = :managerId AND l.status = :status")
    Integer sumDaysByUserManagerIdAndStatus(@Param("managerId") Long managerId, @Param("status") LeaveRequest.LeaveStatus status);
}


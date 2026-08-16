package com.expensemanagement.repository;

import com.expensemanagement.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    @Query("SELECT a FROM ActivityLog a WHERE a.organization.id = :organizationId ORDER BY a.createdAt DESC")
    Page<ActivityLog> findByOrganizationIdOrderByCreatedAtDesc(@Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT a FROM ActivityLog a WHERE a.user.id = :userId ORDER BY a.createdAt DESC")
    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
}


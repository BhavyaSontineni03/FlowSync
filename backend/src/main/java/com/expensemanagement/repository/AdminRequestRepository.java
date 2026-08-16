package com.expensemanagement.repository;

import com.expensemanagement.model.AdminRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, Long> {
    List<AdminRequest> findByOrganizationIdAndStatus(Long organizationId, AdminRequest.RequestStatus status);
}

package com.expensemanagement.repository;

import com.expensemanagement.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    
    List<User> findByOrganizationId(Long organizationId);
    
    org.springframework.data.domain.Page<User> findByOrganizationId(Long organizationId, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE u.organization.id = :orgId AND u.role IN :roles")
    List<User> findByOrganizationIdAndRoleIn(@Param("orgId") Long orgId, @Param("roles") List<User.UserRole> roles);
    
    /**
     * Find all users who report to a specific manager
     */
    List<User> findByManagerId(Long managerId);
    
    /**
     * Find enabled users who report to a specific manager
     */
    List<User> findByManagerIdAndEnabled(Long managerId, Boolean enabled);
    
    /**
     * Find all users assigned to a specific HR
     */
    List<User> findByHrId(Long hrId);
    
    /**
     * Find enabled users assigned to a specific HR
     */
    List<User> findByHrIdAndEnabled(Long hrId, Boolean enabled);
}


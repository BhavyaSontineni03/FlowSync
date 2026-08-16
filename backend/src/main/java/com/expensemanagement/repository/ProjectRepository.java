package com.expensemanagement.repository;

import com.expensemanagement.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByCodeAndOrganizationId(String code, Long organizationId);
    
    Page<Project> findByOrganizationId(Long organizationId, Pageable pageable);
    
    List<Project> findByOrganizationIdAndStatus(Long organizationId, Project.ProjectStatus status);
    
    @Query("SELECT p FROM Project p WHERE p.organization.id = :orgId AND (p.code LIKE %:search% OR p.name LIKE %:search%)")
    Page<Project> searchProjects(@Param("orgId") Long organizationId, @Param("search") String search, Pageable pageable);
    
    /**
     * Find all projects managed by a specific user (manager)
     */
    List<Project> findByManagerId(Long managerId);
    
    /**
     * Find active projects managed by a specific user
     */
    List<Project> findByManagerIdAndStatus(Long managerId, Project.ProjectStatus status);
}

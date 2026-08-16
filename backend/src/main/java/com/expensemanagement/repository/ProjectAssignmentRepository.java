package com.expensemanagement.repository;

import com.expensemanagement.model.ProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {
    List<ProjectAssignment> findByUserId(Long userId);
    
    List<ProjectAssignment> findByUserIdAndIsActiveTrue(Long userId);
    
    List<ProjectAssignment> findByProjectId(Long projectId);
    
    List<ProjectAssignment> findByProjectIdAndIsActiveTrue(Long projectId);
    
    Optional<ProjectAssignment> findByUserIdAndProjectIdAndIsActiveTrue(Long userId, Long projectId);
    
    @Query("SELECT pa FROM ProjectAssignment pa WHERE pa.user.id = :userId AND pa.isActive = true")
    List<ProjectAssignment> findActiveAssignmentsByUser(@Param("userId") Long userId);
    
    /**
     * Find all active assignments for projects managed by a specific manager.
     * This returns all employees currently working on any project that this manager manages.
     */
    @Query("SELECT pa FROM ProjectAssignment pa WHERE pa.project.manager.id = :managerId AND pa.isActive = true")
    List<ProjectAssignment> findActiveAssignmentsByProjectManager(@Param("managerId") Long managerId);
}

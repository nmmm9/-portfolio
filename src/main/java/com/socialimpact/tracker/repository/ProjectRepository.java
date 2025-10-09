package com.socialimpact.tracker.repository;

import com.socialimpact.tracker.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT p FROM Project p WHERE p.organization.id = :orgId")
    List<Project> findByOrganizationId(Long orgId);

    @Query("SELECT p FROM Project p WHERE p.category = :category")
    List<Project> findByCategory(String category);
}


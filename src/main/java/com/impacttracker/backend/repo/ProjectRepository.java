package com.impacttracker.backend.repo;

import com.impacttracker.backend.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOrgId(Long orgId);
}
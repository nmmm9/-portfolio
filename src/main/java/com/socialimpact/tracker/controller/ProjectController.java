package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.dto.ProjectDTO;
import com.socialimpact.tracker.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * GET /api/projects
     * 전체 프로젝트 목록
     */
    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getAllProjects() {
        List<ProjectDTO> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }

    /**
     * GET /api/projects/{id}
     * 프로젝트 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDTO> getProjectById(@PathVariable Long id) {
        ProjectDTO project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }

    /**
     * GET /api/projects/{id}/kpis
     * 특정 프로젝트의 KPI 데이터
     */
    @GetMapping("/{id}/kpis")
    public ResponseEntity<List<ProjectDTO.KpiSummary>> getProjectKpis(@PathVariable Long id) {
        List<ProjectDTO.KpiSummary> kpis = projectService.getProjectKpis(id);
        return ResponseEntity.ok(kpis);
    }
}

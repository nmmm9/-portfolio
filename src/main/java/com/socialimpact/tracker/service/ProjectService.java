package com.socialimpact.tracker.service;

import com.socialimpact.tracker.dto.ProjectDTO;
import com.socialimpact.tracker.entity.KpiReport;
import com.socialimpact.tracker.entity.KpiReport.ReportStatus;
import com.socialimpact.tracker.entity.Project;
import com.socialimpact.tracker.repository.KpiReportRepository;
import com.socialimpact.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final KpiReportRepository kpiReportRepository;

    /**
     * 전체 프로젝트 목록 조회
     */
    public List<ProjectDTO> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 프로젝트 ID로 조회
     */
    public ProjectDTO getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return convertToDTO(project);
    }

    /**
     * 특정 프로젝트의 KPI 데이터 조회
     */
    public List<ProjectDTO.KpiSummary> getProjectKpis(Long projectId) {
        List<KpiReport> reports = kpiReportRepository.findByProjectId(projectId).stream()
                .filter(r -> r.getStatus() == ReportStatus.APPROVED)
                .toList();

        return reports.stream()
                .collect(Collectors.groupingBy(r -> r.getKpi().getName()))
                .entrySet().stream()
                .map(entry -> {
                    String kpiName = entry.getKey();
                    String unit = entry.getValue().get(0).getKpi().getUnit();
                    String totalValue = entry.getValue().stream()
                            .map(KpiReport::getValue)
                            .reduce((a, b) -> a.add(b))
                            .orElse(java.math.BigDecimal.ZERO)
                            .toString();
                    return new ProjectDTO.KpiSummary(kpiName, totalValue, unit);
                })
                .collect(Collectors.toList());
    }

    private ProjectDTO convertToDTO(Project project) {
        List<ProjectDTO.KpiSummary> kpiSummaries = getProjectKpis(project.getId());

        return new ProjectDTO(
                project.getId(),
                project.getName(),
                project.getCategory(),
                project.getOrganization().getName(),
                project.getStartDate(),
                project.getEndDate(),
                kpiSummaries
        );
    }
}

// DatabaseCheckService.java
package com.socialimpact.tracker.service;

import com.socialimpact.tracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

/**
 * 애플리케이션 시작 시 DB에 있는 데이터 확인
 * PowerShell로 이미 저장된 데이터 검증용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseCheckService implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final KpiRepository kpiRepository;
    private final KpiReportRepository kpiReportRepository;

    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("Database Data Check - PowerShell Loaded Data");
        log.info("=================================================");

        long orgCount = organizationRepository.count();
        long projectCount = projectRepository.count();
        long kpiCount = kpiRepository.count();
        long reportCount = kpiReportRepository.count();

        log.info("📊 Organizations: {}", orgCount);
        log.info("📊 Projects: {}", projectCount);
        log.info("📊 KPIs: {}", kpiCount);
        log.info("📊 Reports: {}", reportCount);

        if (orgCount == 0) {
            log.warn("⚠️  No organizations found in database!");
            log.warn("⚠️  Please ensure PowerShell script has loaded data");
        } else {
            log.info("✅ Data successfully loaded from PowerShell");

            // 조직 목록 출력
            log.info("\n📋 Organization List:");
            organizationRepository.findAll().forEach(org ->
                    log.info("  - {}: {} (Type: {})", org.getId(), org.getName(), org.getType())
            );

            // 프로젝트 목록 출력
            if (projectCount > 0) {
                log.info("\n📋 Project List (Top 5):");
                projectRepository.findAll().stream().limit(5).forEach(project ->
                        log.info("  - {}: {} (Category: {})",
                                project.getId(),
                                project.getName(),
                                project.getCategory())
                );
            }

            // KPI 목록 출력
            if (kpiCount > 0) {
                log.info("\n📋 KPI List:");
                kpiRepository.findAll().forEach(kpi ->
                        log.info("  - {}: {} ({})",
                                kpi.getId(),
                                kpi.getName(),
                                kpi.getUnit())
                );
            }

            // 보고서 통계
            if (reportCount > 0) {
                long approvedCount = kpiReportRepository.countApprovedReports();
                log.info("\n📊 Report Statistics:");
                log.info("  - Total Reports: {}", reportCount);
                log.info("  - Approved Reports: {}", approvedCount);
                log.info("  - Approval Rate: {}%",
                        reportCount > 0 ? (approvedCount * 100.0 / reportCount) : 0);
            }
        }

        log.info("=================================================");
        log.info("Application ready to serve API requests");
        log.info("API Base URL: http://localhost:8080/api");
        log.info("Swagger UI: http://localhost:8080/swagger-ui.html");
        log.info("=================================================");
    }
}
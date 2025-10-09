package com.socialimpact.tracker.controller;

import com.socialimpact.tracker.dto.CollectionProgressDTO;
import com.socialimpact.tracker.entity.Kpi;
import com.socialimpact.tracker.entity.KpiReport;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.entity.Project;
import com.socialimpact.tracker.repository.KpiReportRepository;
import com.socialimpact.tracker.repository.KpiRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import com.socialimpact.tracker.repository.ProjectRepository;
import com.socialimpact.tracker.service.DartCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/collection")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CollectionProgressController {

    private final DartCollectorService dartCollectorService;
    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final KpiReportRepository kpiReportRepository;
    private final KpiRepository kpiRepository;

    @GetMapping("/progress")
    public ResponseEntity<CollectionProgressDTO> getCollectionProgress() {
        CollectionProgressDTO progress = CollectionProgressDTO.builder()
                .isCollecting(dartCollectorService.isCollecting())
                .totalCompanies(dartCollectorService.getTotalCompanies().get())
                .processedCompanies(dartCollectorService.getProcessedCompanies().get())
                .successCount(dartCollectorService.getSuccessCount().get())
                .failureCount(dartCollectorService.getFailureCount().get())
                .progressPercentage(dartCollectorService.getProgressPercentage())
                .estimatedTimeRemaining(dartCollectorService.getEstimatedTimeRemaining())
                .elapsedTime((System.currentTimeMillis() - dartCollectorService.getStartTime()) / 1000)
                .build();

        return ResponseEntity.ok(progress);
    }

    @PostMapping("/start")
    public ResponseEntity<String> startCollection() {
        if (dartCollectorService.isCollecting()) {
            return ResponseEntity.badRequest().body("Collection is already in progress");
        }

        new Thread(() -> {
            dartCollectorService.collectAllListedCompanies();
        }).start();

        return ResponseEntity.ok("Data collection started in background");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getCollectionStatus() {
        if (dartCollectorService.isCollecting()) {
            return ResponseEntity.ok(String.format("Collecting: %.1f%% (%d/%d)",
                    dartCollectorService.getProgressPercentage(),
                    dartCollectorService.getProcessedCompanies().get(),
                    dartCollectorService.getTotalCompanies().get()));
        } else {
            return ResponseEntity.ok("Idle");
        }
    }

    @PostMapping("/test-company/{corpCode}")
    public ResponseEntity<String> testCompany(@PathVariable String corpCode) {
        log.info("🧪 Testing company: {}", corpCode);

        dartCollectorService.collectDonationData(corpCode);

        long orgCount = organizationRepository.count();
        long projectCount = projectRepository.count();
        long reportCount = kpiReportRepository.count();

        String result = String.format(
                "Test completed!\nOrganizations: %d\nProjects: %d\nReports: %d",
                orgCount, projectCount, reportCount
        );

        return ResponseEntity.ok(result);
    }

    /**
     * 더미 데이터 생성 테스트
     */
    @PostMapping("/test-dummy")
    public ResponseEntity<String> createDummyData() {
        log.info("🧪 Creating dummy data...");

        try {
            // 1. Organization 생성
            Organization org = new Organization();
            org.setName("테스트회사_" + System.currentTimeMillis());
            org.setType("상장사");
            org = organizationRepository.save(org);
            log.info("✅ Organization created: ID={}, Name={}", org.getId(), org.getName());

            // 2. Project 생성
            Project project = new Project();
            project.setOrganization(org);
            project.setName("테스트 프로젝트 CSR 2024");
            project.setCategory("CSR");
            project.setStartDate(LocalDate.of(2024, 1, 1));
            project = projectRepository.save(project);
            log.info("✅ Project created: ID={}", project.getId());

            // 3. KPI 생성 또는 조회
            Kpi kpi = kpiRepository.findByName("Donation Amount")
                    .orElseGet(() -> {
                        Kpi newKpi = new Kpi();
                        newKpi.setName("Donation Amount");
                        newKpi.setUnit("원");
                        newKpi.setCategory("Finance");
                        Kpi saved = kpiRepository.save(newKpi);
                        log.info("✅ KPI created: ID={}", saved.getId());
                        return saved;
                    });
            log.info("✅ Using KPI: ID={}, Name={}", kpi.getId(), kpi.getName());

            // 4. Report 생성
            KpiReport report = new KpiReport();
            report.setProject(project);
            report.setKpi(kpi);
            report.setValue(BigDecimal.valueOf(5000000)); // 500만원
            report.setReportDate(LocalDate.of(2024, 12, 31));
            report.setStatus(KpiReport.ReportStatus.APPROVED);
            report.setApprovedBy("TEST");
            report.setApprovedAt(LocalDateTime.now());
            report = kpiReportRepository.save(report);
            log.info("✅ Report created: ID={}, Value={}", report.getId(), report.getValue());

            // 결과 확인
            long orgCount = organizationRepository.count();
            long projectCount = projectRepository.count();
            long reportCount = kpiReportRepository.count();

            String result = String.format(
                    "✅ Dummy data created successfully!\n\n" +
                            "📊 Database Status:\n" +
                            "Organizations: %d\n" +
                            "Projects: %d\n" +
                            "Reports: %d\n\n" +
                            "📝 Created:\n" +
                            "Org ID: %d (%s)\n" +
                            "Project ID: %d\n" +
                            "Report ID: %d (Value: %s 원)",
                    orgCount, projectCount, reportCount,
                    org.getId(), org.getName(),
                    project.getId(),
                    report.getId(), report.getValue()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Error creating dummy data", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage() + "\n" +
                    java.util.Arrays.toString(e.getStackTrace()));
        }
    }
}
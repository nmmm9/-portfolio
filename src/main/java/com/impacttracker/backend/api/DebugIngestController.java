package com.impacttracker.backend.api;

import com.impacttracker.backend.repo.OrganizationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugIngestController {

    private final OrganizationRepository orgRepo;

    public DebugIngestController(OrganizationRepository orgRepo) {
        this.orgRepo = orgRepo;
    }

    @GetMapping("/internal/ingest/debug/org-count")
    public String orgCount() {
        long cnt = orgRepo.count();
        return "organization.count=" + cnt;
    }
}

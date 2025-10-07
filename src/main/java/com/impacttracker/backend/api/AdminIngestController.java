package com.impacttracker.backend.api;

import com.impacttracker.backend.config.IngestProps;
import com.impacttracker.backend.ingest.IngestOrchestrator;
import com.impacttracker.backend.repo.KpiMonthlyRepository;
import com.impacttracker.backend.repo.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/admin/ingest")
public class AdminIngestController {

    private final IngestOrchestrator orchestrator;
    private final OrganizationRepository orgRepo;
    private final KpiMonthlyRepository kpiRepo;
    private final IngestProps props;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> lastJob = new AtomicReference<>("-");
    private final AtomicReference<Instant> lastStartedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFinishedAt = new AtomicReference<>();

    public AdminIngestController(IngestOrchestrator orchestrator,
                                 OrganizationRepository orgRepo,
                                 KpiMonthlyRepository kpiRepo,
                                 IngestProps props) {
        this.orchestrator = orchestrator;
        this.orgRepo = orgRepo;
        this.kpiRepo = kpiRepo;
        this.props = props;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "ok", true,
                "running", running.get(),
                "lastJob", lastJob.get(),
                "lastStartedAt", lastStartedAt.get(),
                "lastFinishedAt", lastFinishedAt.get(),
                "profileOnStartup", props.isOnStartup(),
                "yearsBackDefault", props.getYearsBack(),
                "parallelismDefault", props.getParallelism()
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long orgs = orgRepo.count();
        long kpis = kpiRepo.count();
        return Map.of(
                "organizations", orgs,
                "kpiMonthly", kpis
        );
    }

    @PostMapping("/run-now")
    public Map<String, Object> runNow(@RequestParam(defaultValue = "-1") Integer yearsBack,
                                      @RequestParam(defaultValue = "-1") Integer parallelism) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("ok", false, "message", "another job is running");
        }
        try {
            int yb = (yearsBack == null || yearsBack < 0) ? props.getYearsBack() : yearsBack;
            int pl = (parallelism == null || parallelism < 1) ? props.getParallelism() : parallelism;

            lastJob.set("run-now");
            lastStartedAt.set(Instant.now());
            orchestrator.runOnStartupBlocking(yb, pl);
            lastFinishedAt.set(Instant.now());
            return Map.of("ok", true, "yearsBack", yb, "parallelism", pl);
        } finally {
            running.set(false);
        }
    }

    @PostMapping("/backfill/{years}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> backfill(@PathVariable int years) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("ok", false, "message", "another job is running");
        }
        lastJob.set("backfill:" + years);
        lastStartedAt.set(Instant.now());

        new Thread(() -> {
            try {
                orchestrator.backfillAllListed(years);
                lastFinishedAt.set(Instant.now());
            } catch (Exception ignore) {
                lastFinishedAt.set(Instant.now());
            } finally {
                running.set(false);
            }
        }, "admin-backfill-" + years).start();

        return Map.of("ok", true, "accepted", true, "years", years);
    }

    @GetMapping("/running")
    public Map<String, Object> running() {
        return Map.of("running", running.get(), "lastJob", lastJob.get());
    }
}
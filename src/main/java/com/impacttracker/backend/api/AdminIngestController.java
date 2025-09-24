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

    /** 간단 헬스체크 */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "ok", true,
                "running", running.get(),
                "lastJob", lastJob.get(),
                "lastStartedAt", lastStartedAt.get(),
                "lastFinishedAt", lastFinishedAt.get(),
                "profileOnStartup", props.isOnStartup(),
                "monthsBackDefault", props.getMonthsBack(),
                "parallelismDefault", props.getParallelism()
        );
    }

    /** 현재 DB 대략 통계 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long orgs = orgRepo.count();
        long kpis = kpiRepo.count();
        return Map.of(
                "organizations", orgs,
                "kpiMonthly", kpis
        );
    }

    /** 즉시 1회 실행 (동기) — 앱 기본 동작과 동일한 경로 */
    @PostMapping("/run-now")
    public Map<String, Object> runNow(@RequestParam(defaultValue = "-1") Integer monthsBack,
                                      @RequestParam(defaultValue = "-1") Integer parallelism) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("ok", false, "message", "another job is running");
        }
        try {
            int mb = (monthsBack == null || monthsBack < 0) ? props.getMonthsBack() : monthsBack;
            int pl = (parallelism == null || parallelism < 1) ? props.getParallelism() : parallelism;

            lastJob.set("run-now");
            lastStartedAt.set(Instant.now());
            orchestrator.runOnStartupBlocking(mb, pl);
            lastFinishedAt.set(Instant.now());
            return Map.of("ok", true, "monthsBack", mb, "parallelism", pl);
        } finally {
            running.set(false);
        }
    }

    /** 백필 시작 (비동기) — 과거 N개월 ~ 현재까지 상장사 전수 */
    @PostMapping("/backfill/{months}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> backfill(@PathVariable int months) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("ok", false, "message", "another job is running");
        }
        lastJob.set("backfill:" + months);
        lastStartedAt.set(Instant.now());

        new Thread(() -> {
            try {
                orchestrator.backfillAllListed(months);
                lastFinishedAt.set(Instant.now());
            } catch (Exception ignore) {
                lastFinishedAt.set(Instant.now());
            } finally {
                running.set(false);
            }
        }, "admin-backfill-" + months).start();

        return Map.of("ok", true, "accepted", true, "months", months);
    }

    /** 실행 중인지 확인 */
    @GetMapping("/running")
    public Map<String, Object> running() {
        return Map.of("running", running.get(), "lastJob", lastJob.get());
    }
}

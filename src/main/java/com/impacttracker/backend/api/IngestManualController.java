package com.impacttracker.backend.api;

import com.impacttracker.backend.ingest.IngestOrchestrator;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ingest")
public class IngestManualController {
    private final IngestOrchestrator orchestrator;
    public IngestManualController(IngestOrchestrator orchestrator){ this.orchestrator = orchestrator; }

    @PostMapping("/run-now")
    public String runNow(@RequestParam(defaultValue = "0") int monthsBack,
                         @RequestParam(defaultValue = "2") int parallelism) {
        orchestrator.runOnStartupBlocking(monthsBack, parallelism);
        return "ok";
    }

    @PostMapping("/backfill/{months}")
    public String backfill(@PathVariable int months) {
        new Thread(() -> orchestrator.backfillAllListed(months), "backfill-run").start();
        return "started";
    }
}

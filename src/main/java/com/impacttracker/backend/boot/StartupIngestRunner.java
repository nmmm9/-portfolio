package com.impacttracker.backend.boot;

import com.impacttracker.backend.ingest.IngestOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupIngestRunner implements ApplicationRunner {

    private final IngestOrchestrator orchestrator;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ingest] ⚡ StartupIngestRunner 실행 시작");
        try {
            orchestrator.runOnStartupBlocking();
            log.info("[ingest] ✅ StartupIngestRunner 완료");
        } catch (Exception e) {
            log.error("[ingest] ❌ StartupIngestRunner 실패", e);
        }
    }
}
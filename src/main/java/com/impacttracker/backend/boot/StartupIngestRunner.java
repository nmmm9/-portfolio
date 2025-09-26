package com.impacttracker.backend.boot;

import com.impacttracker.backend.ingest.IngestOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "impacttracker.ingest.startup-enabled", havingValue = "true")
@RequiredArgsConstructor
public class StartupIngestRunner implements ApplicationRunner {

    private final IngestOrchestrator orchestrator;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ingest] startup-enabled=true → 앱 기동시 자동 수집 실행");
        orchestrator.runOnStartupBlocking();
        log.info("[ingest] startup ingest done");
    }
}

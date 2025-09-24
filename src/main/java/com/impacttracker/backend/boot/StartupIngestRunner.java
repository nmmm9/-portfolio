package com.impacttracker.backend.boot;

import com.impacttracker.backend.config.IngestProps;
import com.impacttracker.backend.ingest.IngestOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class StartupIngestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIngestRunner.class);

    private final IngestProps props;
    private final IngestOrchestrator orchestrator;

    public StartupIngestRunner(IngestProps props, IngestOrchestrator orchestrator) {
        this.props = props;
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props == null || !props.isOnStartup()) {
            log.info("[ingest] 앱 기동 - 자동 수집 비활성화(on-startup=false). 스킵합니다.");
            return;
        }

        int months = Math.max(0, props.getMonthsBack());
        int parallelism = Math.max(1, props.getParallelism());
        log.info("[ingest] 앱 기동 - 자동 수집 시작 (monthsBack={}, parallelism={})", months, parallelism);

        try {
            // 네트워크 이슈로 실패해도 앱은 계속 떠 있어야 한다.
            orchestrator.runOnStartupBlocking(months, parallelism);
            log.info("[ingest] 앱 기동 - 자동 수집 완료");
        } catch (Exception e) {
            // 여기서 절대 예외를 바깥으로 던지지 않는다.
            log.warn("[ingest] 앱 기동 - 자동 수집 실패(무시하고 기동 계속): {}", e.toString());
            log.debug("[ingest] 상세 스택트레이스", e);
        }
    }
}

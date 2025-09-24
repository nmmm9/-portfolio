package com.impacttracker.backend.scheduler;

import com.impacttracker.backend.ingest.IngestOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스케줄러:
 * - 매일 03:20 KST: 최근 1개월치 전 상장사 재수집(누락/지연 반영)
 * - 매주 월요일 04:00 KST: 과거 24개월 백필(비싸므로 주 1회)
 *   → 병렬성은 IngestProps.parallelism을 따름
 */
@Component
@EnableScheduling
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final IngestOrchestrator orchestrator;

    public IngestScheduler(IngestOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** 매일 새벽 03:20 (Asia/Seoul) — 최근 1개월 전수 */
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
    public void dailyRecentMonth() {
        log.info("[sched] dailyRecentMonth start");
        orchestrator.backfillAllListed(1);
        log.info("[sched] dailyRecentMonth done");
    }

    /** 매주 월요일 04:00 (Asia/Seoul) — 과거 24개월 백필 */
    @Scheduled(cron = "0 0 4 * * MON", zone = "Asia/Seoul")
    public void weeklyBackfill24m() {
        log.info("[sched] weeklyBackfill24m start");
        orchestrator.backfillAllListed(24);
        log.info("[sched] weeklyBackfill24m done");
    }
}

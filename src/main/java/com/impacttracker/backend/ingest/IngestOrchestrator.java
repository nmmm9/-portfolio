// src/main/java/com/impacttracker/backend/ingest/IngestOrchestrator.java
package com.impacttracker.backend.ingest;

import com.impacttracker.backend.config.IngestProps;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartQuotaExceededException;
import com.impacttracker.backend.ingest.dart.CorpCodeDownloader;
import com.impacttracker.backend.repo.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class IngestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestOrchestrator.class);

    private final CorpCodeDownloader corpCodeDownloader;
    private final OrganizationRepository orgRepo;
    private final DartIngestService dartService;
    private final IngestProps props;

    // ★ 쿼터 히트 시 모든 남은 태스크 중단 신호
    private static final AtomicBoolean QUOTA_HIT = new AtomicBoolean(false);

    public IngestOrchestrator(CorpCodeDownloader corpCodeDownloader,
                              OrganizationRepository orgRepo,
                              DartIngestService dartService,
                              IngestProps props) {
        this.corpCodeDownloader = corpCodeDownloader;
        this.orgRepo = orgRepo;
        this.dartService = dartService;
        this.props = props;
    }

    public void runOnStartupBlocking() {
        runOnStartupBlocking(props != null ? props.getMonthsBack() : 0, defaultParallelism());
    }

    public void runOnStartupBlocking(int monthsBack, int parallelism) {
        try {
            log.info("[ingest] [startup] begin monthsBack={} parallelism={}", monthsBack, parallelism);
            List<CorpCodeDownloader.Corp> corps = corpCodeDownloader.downloadAllCorps();
            upsertOrganizationsInBatches(corps, 1000);
            backfillAllListed(monthsBack, parallelism);
            log.info("[ingest] [startup] done");
        } catch (Exception e) {
            log.error("[ingest] startup fatal", e);
            throw e;
        }
    }

    /** 기존 공개 엔트리 (monthsBack만 지정) */
    public void backfillAllListed(int monthsBack) {
        int p = (props != null && props.getParallelism() > 0) ? props.getParallelism() : defaultParallelism();
        backfillAllListed(monthsBack, p);
    }

    /** 병렬도까지 지정하는 버전 (쿼터 가드 적용) */
    public void backfillAllListed(int monthsBack, int parallelism) {
        QUOTA_HIT.set(false); // 매 실행마다 초기화

        List<Organization> all = orgRepo.findAllDartEnabled(); // ※ 이 메서드는 상장사만 반환하도록 수정되어 있어야 함
        if (all == null || all.isEmpty()) {
            log.info("[ingest] no DART-enabled organizations");
            return;
        }
        if (monthsBack < 0) monthsBack = 0;
        if (parallelism <= 0) parallelism = defaultParallelism();

        // 대상 기업 상한(기본: 무제한)
        int cap = parseInt(System.getProperty("ingest.maxTargets"), Integer.MAX_VALUE);
        List<Organization> targets = all.stream()
                .filter(o -> o.getCorpCode() != null && !o.getCorpCode().isBlank())
                .sorted(Comparator.comparing(Organization::getId))
                .limit(cap)
                .toList();

        YearMonth now = YearMonth.now();
        List<YearMonth> months = new ArrayList<>(monthsBack + 1);
        for (int i = 0; i <= monthsBack; i++) {
            months.add(now.minusMonths(i));
        }

        // ★ 운영 팁: 보고서 많은 달(3, 8, 11)을 우선 처리 (원하면 -Dingest.preferReportMonths=false 로 끄기)
        boolean preferHeavyMonths = Boolean.parseBoolean(System.getProperty("ingest.preferReportMonths", "true"));
        if (preferHeavyMonths) {
            months.sort(Comparator.comparingInt(IngestOrchestrator::monthPriority));
        }

        log.info("[ingest] targets capped {}/{} (limit={}), months={}",
                targets.size(), all.size(), cap, months.size());

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (Organization org : targets) {
                String corpCode = org.getCorpCode();
                for (YearMonth ym : months) {
                    tasks.add(() -> {
                        if (QUOTA_HIT.get()) return 0; // 쿼터 초과 이후 즉시 중단

                        try {
                            int sleepMs = parseInt(System.getProperty("ingest.sleepMs"), 600);
                            long jitter = (long)(Math.random() * 300); // +0~300ms
                            try { Thread.sleep(sleepMs + jitter); } catch (InterruptedException ignored) {}                            return dartService.ingestDonationForCorp(corpCode, org, ym);
                        } catch (DartQuotaExceededException q) {
                            QUOTA_HIT.set(true);
                            log.warn("[ingest][quota] OpenDART quota exceeded. Halting remaining tasks.");
                            return 0;
                        } catch (Exception ex) {
                            log.warn("[ingest][DART] skip corp={} ym={} cause={}", corpCode, ym, ex.toString());
                            try { Thread.sleep(800L); } catch (InterruptedException ignored) {}
                            return 0;
                        }
                    });
                }
            }

            List<Future<Integer>> futures = pool.invokeAll(tasks);
            int ingested = 0;
            for (Future<Integer> f : futures) {
                try {
                    Integer r = f.get();
                    if (r != null) ingested += r;
                } catch (Exception e) {
                    log.warn("[ingest] task failed: {}", e.toString());
                }
            }
            log.info("[ingest] backfill done, new rows ingested={} quotaHit={}", ingested, QUOTA_HIT.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("[ingest] interrupted", ie);
        } catch (Exception e) {
            log.error("[ingest] backfill fatal", e);
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }
    }

    private static int monthPriority(YearMonth ym) {
        int m = ym.getMonthValue();
        if (m == 3)  return 0; // 사업보고서
        if (m == 8)  return 1; // 반기
        if (m == 11) return 2; // 3분기
        return 9;
    }

    /* ========= 조직 업서트(기존 로직 유지) ========= */

    public void upsertOrganizationsInBatches(List<CorpCodeDownloader.Corp> corps, int batchSize) {
        if (corps == null || corps.isEmpty()) {
            log.info("[ingest][startup] corp list is empty");
            return;
        }
        if (batchSize <= 0) batchSize = 1000;

        Map<String, CorpCodeDownloader.Corp> byCorp = corps.stream()
                .filter(c -> c.corpCode() != null && !c.corpCode().isBlank())
                .collect(Collectors.toMap(CorpCodeDownloader.Corp::corpCode, c -> c, (a, b) -> a));

        List<String> allCodes = new ArrayList<>(byCorp.keySet());
        int total = allCodes.size();
        log.info("[ingest][startup] corpCode unique size={}", total);

        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger updated  = new AtomicInteger();
        AtomicInteger processed= new AtomicInteger();

        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<String> sliceCodes = allCodes.subList(i, end);

            List<Organization> existingList = orgRepo.findAllByCorpCodeIn(sliceCodes);
            Map<String, Organization> existingByCorp = new HashMap<>();
            for (Organization o : existingList) existingByCorp.put(o.getCorpCode(), o);

            List<Organization> toSave = new ArrayList<>(sliceCodes.size());
            for (String code : sliceCodes) {
                CorpCodeDownloader.Corp row = byCorp.get(code);
                Organization o = existingByCorp.get(code);
                if (o == null) {
                    o = new Organization();
                    o.setCorpCode(row.corpCode());
                    o.setName(row.corpName());
                    o.setStockCode(row.stockCode());
                    toSave.add(o);
                    inserted.incrementAndGet();
                } else {
                    boolean dirty = false;
                    if (row.corpName() != null && !row.corpName().equals(o.getName())) { o.setName(row.corpName()); dirty = true; }
                    if (row.stockCode() != null && !row.stockCode().equals(o.getStockCode())) { o.setStockCode(row.stockCode()); dirty = true; }
                    if (dirty) { toSave.add(o); updated.incrementAndGet(); }
                }
            }
            if (!toSave.isEmpty()) orgRepo.saveAll(toSave);
            int done = processed.addAndGet(sliceCodes.size());
            if (done % 10000 == 0 || end == total) {
                log.info("[ingest][startup] org upsert progress done={} / total={}, inserted={}, updated={}",
                        done, total, inserted.get(), updated.get());
            }
        }
        log.info("[ingest][startup] corpCode sync: inserted={} updated={}", inserted.get(), updated.get());
    }

    private int defaultParallelism() {
        int cpu = Math.max(2, Runtime.getRuntime().availableProcessors());
        if (props == null) return 1; // 운영 팁: 기본 1로 낮춰 쿼터 보호
        int p = props.getParallelism();
        return (p > 0) ? p : 1;
    }

    private int parseInt(String s, int defV) {
        if (s == null) return defV;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return defV; }
    }
}

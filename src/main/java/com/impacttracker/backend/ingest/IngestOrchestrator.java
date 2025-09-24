package com.impacttracker.backend.ingest;

import com.impacttracker.backend.config.IngestProps;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.CorpCodeDownloader;
import com.impacttracker.backend.ingest.DartIngestService;
import com.impacttracker.backend.repo.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class IngestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestOrchestrator.class);

    private final CorpCodeDownloader corpCodeDownloader;
    private final OrganizationRepository orgRepo;
    private final DartIngestService dartService;
    private final IngestProps props;

    public IngestOrchestrator(CorpCodeDownloader corpCodeDownloader,
                              OrganizationRepository orgRepo,
                              DartIngestService dartService,
                              IngestProps props) {
        this.corpCodeDownloader = corpCodeDownloader;
        this.orgRepo = orgRepo;
        this.dartService = dartService;
        this.props = props;
    }

    /** 앱 기동 시 동기 실행 엔트리 */
    public void runOnStartupBlocking() {
        runOnStartupBlocking(props != null ? props.getMonthsBack() : 0,
                defaultParallelism());
    }

    public void runOnStartupBlocking(int monthsBack, int parallelism) {
        try {
            log.info("[ingest] [startup] begin monthsBack={} parallelism={}", monthsBack, parallelism);
            List<CorpCodeDownloader.Corp> corps = corpCodeDownloader.downloadAllCorps(); // 11만+
            upsertOrganizationsInBatches(corps, 1000);
            backfillAllListed(monthsBack, parallelism);
            log.info("[ingest] [startup] done");
        } catch (Exception e) {
            log.error("[ingest] startup fatal", e);
            throw e;
        }
    }

    /* =======================
       공개 진입 메서드(호출부 호환)
       ======================= */

    /** 기존 컨트롤러/스케줄러가 호출하는 시그니처 유지 */
    public void backfillAllListed(int monthsBack) {
        int p = (props != null && props.getParallelism() > 0)
                ? props.getParallelism()
                : defaultParallelism();
        backfillAllListed(monthsBack, p);
    }

    /** 병렬도까지 지정하는 버전 */
    public void backfillAllListed(int monthsBack, int parallelism) {
        List<Organization> targets = orgRepo.findAllDartEnabled();
        if (targets == null || targets.isEmpty()) {
            log.info("[ingest] no DART-enabled organizations");
            return;
        }
        if (monthsBack < 0) monthsBack = 0;
        if (parallelism <= 0) parallelism = defaultParallelism();

        YearMonth now = YearMonth.now();
        List<YearMonth> months = new ArrayList<>(monthsBack + 1);
        for (int i = 0; i <= monthsBack; i++) {
            months.add(now.minusMonths(i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (Organization org : targets) {
                String corpCode = org.getCorpCode();
                if (corpCode == null || corpCode.isBlank()) continue;
                for (YearMonth ym : months) {
                    tasks.add(() -> dartService.ingestDonationForCorp(corpCode, org, ym));
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
            log.info("[ingest] backfill done, new rows ingested={}", ingested);
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

    /* =======================
       내부: 조직 업서트
       ======================= */

    /** corp 목록을 org 테이블과 동기화 */
    private void upsertOrganizationsInBatches(List<CorpCodeDownloader.Corp> corps, int batchSize) {
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

            // 기존 존재하는 조직 조회
            List<Organization> existingList = orgRepo.findAllByCorpCodeIn(sliceCodes);
            Map<String, Organization> existingByCorp = new HashMap<>();
            for (Organization o : existingList) {
                existingByCorp.put(o.getCorpCode(), o);
            }

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
                    if (dirty) {
                        toSave.add(o);
                        updated.incrementAndGet();
                    }
                }
            }
            if (!toSave.isEmpty()) orgRepo.saveAll(toSave);
            int done = processed.addAndGet(sliceCodes.size());
            if (done % 10000 == 0 || end == total) {
                log.info("[ingest][startup] org upsert progress done={} / total={}, inserted={}, updated={}", done, total, inserted.get(), updated.get());
            }
        }
        log.info("[ingest][startup] corpCode sync: inserted={} updated={}", inserted.get(), updated.get());
    }

    /* =======================
       유틸
       ======================= */
    private int defaultParallelism() {
        // props 없거나 값 0/음수면 CPU 기준 기본값
        int cpu = Math.max(2, Runtime.getRuntime().availableProcessors());
        if (props == null) return cpu;
        int p = props.getParallelism();
        return p > 0 ? p : cpu;
    }
}

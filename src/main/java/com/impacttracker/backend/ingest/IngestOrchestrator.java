package com.impacttracker.backend.ingest;

import com.impacttracker.backend.config.IngestProps;
import com.impacttracker.backend.domain.Organization;
import com.impacttracker.backend.ingest.dart.DartQuotaExceededException;
import com.impacttracker.backend.ingest.dart.CorpCodeDownloader;
import com.impacttracker.backend.repo.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.concurrent.*;
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
        runOnStartupBlocking(props != null ? props.getYearsBack() : 3, defaultParallelism());
    }

    public void runOnStartupBlocking(int yearsBack, int parallelism) {
        try {
            log.info("[ingest] 🚀 [startup] begin yearsBack={} parallelism={}", yearsBack, parallelism);

            List<CorpCodeDownloader.Corp> corps = corpCodeDownloader.downloadAllCorps();
            log.info("[ingest] 📥 downloaded {} corps", corps.size());

            // 상장사만 필터링
            List<CorpCodeDownloader.Corp> listedCorps = corps.stream()
                    .filter(c -> c.stockCode() != null && !c.stockCode().isBlank())
                    .toList();
            log.info("[ingest] 🏢 filtered {} listed companies", listedCorps.size());

            // DB에 상장사만 저장
            upsertOrganizationsInBatches(listedCorps, 1000);

            // 연도별 수집
            backfillAllListed(yearsBack, parallelism);

            log.info("[ingest] ✅ [startup] done");
        } catch (Exception e) {
            log.error("[ingest] ❌ startup fatal", e);
            throw e;
        }
    }

    public void backfillAllListed(int yearsBack) {
        int p = (props != null && props.getParallelism() > 0) ? props.getParallelism() : defaultParallelism();
        backfillAllListed(yearsBack, p);
    }

    /**
     * 연도별 재무제표 수집
     */
    public void backfillAllListed(int yearsBack, int parallelism) {
        QUOTA_HIT.set(false);

        List<Organization> all = orgRepo.findAllDartEnabled();
        if (all == null || all.isEmpty()) {
            log.info("[ingest] no DART-enabled organizations");
            return;
        }

        if (yearsBack < 0) yearsBack = 3;
        if (parallelism <= 0) parallelism = defaultParallelism();

        int maxTargets = props != null ? props.getMaxTargets() : 50;

        // 상장사 중 대기업 우선
        List<Organization> targets = all.stream()
                .filter(o -> o.getCorpCode() != null && !o.getCorpCode().isBlank())
                .filter(o -> o.getStockCode() != null && !o.getStockCode().isBlank())
                .sorted(Comparator.comparingInt(this::getCorporateRank)
                        .thenComparing(Organization::getName))
                .limit(maxTargets)
                .toList();

        if (targets.isEmpty()) {
            log.warn("[ingest] no listed companies found");
            return;
        }

        // 수집 대상 연도 목록
        int currentYear = Year.now().getValue();
        List<Integer> years = new ArrayList<>(yearsBack + 1);
        for (int i = 0; i <= yearsBack; i++) {
            years.add(currentYear - i);
        }

        log.info("[ingest] 📋 targets={}/{} (limit={}), years={}, parallelism={}",
                targets.size(), all.size(), maxTargets, years.size(), parallelism);

        String targetNames = targets.stream()
                .limit(10)
                .map(Organization::getName)
                .collect(Collectors.joining(", "));
        log.info("[ingest] 🎯 companies: [{}{}]",
                targetNames, targets.size() > 10 ? ", ..." : "");

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();

            // 각 기업 × 각 연도
            for (Organization org : targets) {
                String corpCode = org.getCorpCode();
                for (Integer year : years) {
                    tasks.add(() -> {
                        if (QUOTA_HIT.get()) return 0;

                        try {
                            // 요청 간 간격
                            Thread.sleep(200 + (long)(Math.random() * 100));

                            return dartService.ingestDonationForYear(corpCode, org, year);
                        } catch (DartQuotaExceededException q) {
                            QUOTA_HIT.set(true);
                            log.warn("[ingest][quota] ⚠️ OpenDART quota exceeded");
                            return 0;
                        } catch (Exception ex) {
                            log.warn("[ingest] ⚠️ skip corp={} year={} cause={}",
                                    corpCode, year, ex.toString());
                            try { Thread.sleep(300L); } catch (InterruptedException ignored) {}
                            return 0;
                        }
                    });
                }
            }

            log.info("[ingest] 🔄 submitting {} tasks...", tasks.size());
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

            log.info("[ingest] ✅ backfill done, new rows ingested={} quotaHit={}",
                    ingested, QUOTA_HIT.get());
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

    private int getCorporateRank(Organization org) {
        String name = org.getName();
        if (name == null) return 999;

        if (name.contains("삼성")) return 1;
        if (name.contains("LG")) return 2;
        if (name.contains("현대")) return 3;
        if (name.contains("SK")) return 4;
        if (name.contains("롯데")) return 10;
        if (name.contains("포스코") || name.contains("POSCO")) return 11;
        if (name.contains("한화")) return 12;
        if (name.contains("GS")) return 13;
        if (name.contains("신세계")) return 14;
        if (name.contains("CJ")) return 15;
        if (name.contains("은행")) return 20;
        if (name.contains("증권")) return 21;
        if (name.contains("생명") || name.contains("손해보험")) return 22;
        if (name.contains("전자")) return 30;
        if (name.contains("자동차")) return 31;
        if (name.contains("화학")) return 32;
        if (name.contains("건설")) return 33;

        return 100;
    }

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
        log.info("[ingest][startup] 📊 corpCode unique size={}", total);

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
                    if (row.corpName() != null && !row.corpName().equals(o.getName())) {
                        o.setName(row.corpName());
                        dirty = true;
                    }
                    if (row.stockCode() != null && !row.stockCode().equals(o.getStockCode())) {
                        o.setStockCode(row.stockCode());
                        dirty = true;
                    }
                    if (dirty) {
                        toSave.add(o);
                        updated.incrementAndGet();
                    }
                }
            }

            if (!toSave.isEmpty()) orgRepo.saveAll(toSave);

            int done = processed.addAndGet(sliceCodes.size());
            if (done % 10000 == 0 || end == total) {
                log.info("[ingest][startup] org upsert progress done={}/{}, inserted={}, updated={}",
                        done, total, inserted.get(), updated.get());
            }
        }
        log.info("[ingest][startup] ✅ corpCode sync: inserted={} updated={}",
                inserted.get(), updated.get());
    }

    private int defaultParallelism() {
        if (props == null) return 2;
        int p = props.getParallelism();
        return (p > 0) ? p : 2;
    }
}
package com.socialimpact.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.socialimpact.tracker.entity.*;
import com.socialimpact.tracker.entity.KpiReport.ReportStatus;
import com.socialimpact.tracker.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCollectorService {

    private final WebClient.Builder webClientBuilder;
    public final OrganizationRepository organizationRepository;
    public final ProjectRepository projectRepository;
    public final KpiRepository kpiRepository;
    public final KpiReportRepository kpiReportRepository;


    @Value("${opendart.api-key}")
    private String dartApiKey;

    @Value("${opendart.base-url}")
    private String dartBaseUrl;

    @Value("${ingest.donation.from-year}")
    private int fromYear;

    @Value("${ingest.donation.to-year}")
    private int toYear;

    @Value("${ingest.parallelism:4}")
    private int parallelism;

    @Getter
    private final AtomicInteger totalCompanies = new AtomicInteger(0);
    @Getter
    private final AtomicInteger processedCompanies = new AtomicInteger(0);
    @Getter
    private final AtomicInteger successCount = new AtomicInteger(0);
    @Getter
    private final AtomicInteger failureCount = new AtomicInteger(0);
    @Getter
    private volatile boolean isCollecting = false;
    @Getter
    private volatile long startTime = 0;

    public List<String> fetchAllCorpCodes() {
        log.info("📥 Fetching all corporation codes from DART...");
        log.info("🔑 Using API Key: {}...", dartApiKey.substring(0, 10));

        try {
            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();

            byte[] zipData = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/corpCode.xml")
                            .queryParam("crtfc_key", dartApiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (zipData == null || zipData.length == 0) {
                log.error("❌ Received empty response from DART API");
                return new ArrayList<>();
            }

            log.info("📦 ZIP file size: {} bytes", zipData.length);

            if (zipData.length < 1000) {
                log.error("❌ ZIP file too small ({}bytes), might be an error response", zipData.length);
                log.error("Response: {}", new String(zipData, "UTF-8"));
                return new ArrayList<>();
            }

            String xmlContent = unzipCorpCode(zipData);
            List<String> corpCodes = parseCorpCodesFromXml(xmlContent);
            log.info("✅ Successfully fetched {} corporation codes", corpCodes.size());

            return corpCodes;

        } catch (Exception e) {
            log.error("❌ Error fetching corp codes from DART", e);
            return new ArrayList<>();
        }
    }
    private String unzipCorpCode(byte[] zipData) throws Exception {
        log.info("🔓 Unzipping... {} bytes", zipData.length);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            int entryCount = 0;

            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                log.info("📄 Found entry #{}: {}", entryCount, entry.getName());

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String content = baos.toString("UTF-8");
                log.info("✅ Extracted {} bytes from {}", content.length(), entry.getName());
                return content;
            }

            log.error("❌ No entries found in ZIP, entry count: {}", entryCount);
        }

        throw new Exception("No entry found in ZIP file");
    }

    private List<String> parseCorpCodesFromXml(String xml) {
        List<String> corpCodes = new ArrayList<>();

        try {
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode root = xmlMapper.readTree(xml);
            JsonNode list = root.get("list");

            if (list != null && list.isArray()) {
                log.info("📊 Found {} items in array", list.size());

                for (JsonNode item : list) {
                    if (!item.has("corp_code")) continue;

                    String corpCode = item.get("corp_code").asText();
                    String stockCode = item.has("stock_code") ? item.get("stock_code").asText().trim() : "";

                    // 상장사 필터링: stock_code가 있고 숫자로만 구성된 경우
                    if (!stockCode.isEmpty() && stockCode.matches("\\d{6}")) {
                        corpCodes.add(corpCode);
                    }
                }

                log.info("✅ Total items: {}, Listed companies: {}", list.size(), corpCodes.size());
            }

        } catch (Exception e) {
            log.error("Error parsing XML", e);
        }

        return corpCodes;
    }

    @Transactional
    public void collectDonationData(String corpCode) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();

            String companyJson = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/company.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode companyInfo = mapper.readTree(companyJson);

            String status = companyInfo.path("status").asText();
            if (!"000".equals(status)) {
                failureCount.incrementAndGet();
                return;
            }

            String corpName = companyInfo.path("corp_name").asText();
            String stockCode = companyInfo.path("stock_code").asText();

            Organization org = saveOrUpdateOrganization(corpName, stockCode);

            boolean hasData = false;
            for (int year = fromYear; year <= toYear; year++) {
                if (collectDonationForYear(webClient, mapper, corpCode, org, year)) {
                    hasData = true;
                }
            }

            if (hasData) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }

        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("⚠️  Rate limit hit for corp: {}, will retry", corpCode);
            try {
                Thread.sleep(5000);
                collectDonationData(corpCode);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                failureCount.incrementAndGet();
            }
        } catch (Exception e) {
            failureCount.incrementAndGet();
        } finally {
            processedCompanies.incrementAndGet();
        }
    }

    private boolean collectDonationForYear(WebClient webClient, ObjectMapper mapper,
                                           String corpCode, Organization org, int year) {
        try {
            // ⭐ 연도별 공시 검색 (제출일 기준)
            // 2024년 = 2024년 1월 ~ 12월에 제출된 공시
            String bgnDe = year + "0101";
            String endDe = year + "1231";

            String listJson = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/list.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", bgnDe)
                            .queryParam("end_de", endDe)
                            .queryParam("pblntf_ty", "A")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode listRoot = mapper.readTree(listJson);
            JsonNode reports = listRoot.path("list");

            if (!reports.isArray() || reports.size() == 0) {
                log.debug("❌ No reports found for {} in {}", corpCode, year);
                return false;
            }

            log.debug("📊 Found {} reports for year {}", reports.size(), year);

            for (JsonNode report : reports) {
                String reportNm = report.path("report_nm").asText();

                // ⭐ 사업보고서만 처리
                if (reportNm.contains("사업보고서")) {
                    String rceptNo = report.path("rcept_no").asText();
                    String rceptDt = report.path("rcept_dt").asText();

                    log.debug("📄 Processing report: {} (접수일: {})", reportNm, rceptDt);

                    BigDecimal donationAmount = extractDonationAmountFromReport(webClient, rceptNo);

                    if (donationAmount != null && donationAmount.compareTo(BigDecimal.ZERO) > 0) {
                        // ⭐ 사업보고서의 '사업연도'를 추출 (예: "사업보고서 (2023.12)" → 2023)
                        int businessYear = year - 1; // 일반적으로 전년도 데이터

                        // 보고서명에서 연도 추출 시도
                        if (reportNm.matches(".*\\((\\d{4})\\..*")) {
                            String yearStr = reportNm.replaceAll(".*\\((\\d{4})\\..*", "$1");
                            businessYear = Integer.parseInt(yearStr);
                        }

                        log.info("✅ {} (사업연도: {}): {} 원",
                                org.getName(), businessYear, donationAmount);

                        saveKpiReport(org, donationAmount, businessYear);
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error collecting data for {} year {}: {}", corpCode, year, e.getMessage());
            return false;
        }
    }


    private BigDecimal extractDonationAmountFromReport(WebClient webClient, String rceptNo) {
        try {
            log.info("📄 Fetching report document: {}", rceptNo);

            String documentHtml = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/document.xml")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("rcept_no", rceptNo)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (documentHtml == null || documentHtml.isEmpty()) {
                log.warn("❌ Empty document for {}", rceptNo);
                return null;
            }

            log.info("📄 Document size: {} bytes", documentHtml.length());

            // ⭐ "기부금" 텍스트 포함 여부 확인
            if (documentHtml.contains("기부금")) {
                log.info("✅ Document contains '기부금' keyword");

                // "기부금" 근처 텍스트 출력
                int idx = documentHtml.indexOf("기부금");
                int start = Math.max(0, idx - 300);
                int end = Math.min(documentHtml.length(), idx + 500);
                String context = documentHtml.substring(start, end);

                log.info("📝 Context around '기부금':\n{}", context);
            } else {
                log.warn("❌ Document does NOT contain '기부금' keyword");
            }

            // ⭐ Jsoup으로 HTML 파싱
            Document doc = Jsoup.parse(documentHtml);

            // 모든 테이블 찾기
            Elements tables = doc.select("table");
            log.info("📊 Found {} tables in document", tables.size());

            int tableIndex = 0;
            for (Element table : tables) {
                tableIndex++;

                Elements rows = table.select("tr");

                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    Element row = rows.get(rowIndex);
                    Elements cells = row.select("td, th");

                    if (cells.size() >= 2) {
                        String firstCell = cells.get(0).text().trim();
                        String secondCell = cells.get(1).text().trim();

                        // "기부" 키워드 포함된 행 모두 출력
                        if (firstCell.contains("기부") || firstCell.contains("사회공헌")) {
                            log.info("🔍 Table #{}, Row #{}: '{}' = '{}'",
                                    tableIndex, rowIndex, firstCell, secondCell);

                            // 숫자 추출 시도
                            String numericValue = secondCell.replaceAll("[^0-9]", "");

                            if (!numericValue.isEmpty() && numericValue.length() >= 3) {
                                try {
                                    BigDecimal amount = new BigDecimal(numericValue);

                                    // 백만원 단위로 변환
                                    BigDecimal actualAmount = amount.multiply(BigDecimal.valueOf(1_000_000));

                                    log.info("💰 Extracted number: {} → {} 백만원 → {} 원",
                                            secondCell, amount, actualAmount);

                                    // 1백만원 ~ 100조원 사이면 유효
                                    if (actualAmount.compareTo(BigDecimal.valueOf(1_000_000L)) >= 0 &&
                                            actualAmount.compareTo(BigDecimal.valueOf(100_000_000_000_000L)) <= 0) {

                                        log.info("✅ VALID donation amount found!");
                                        return actualAmount;
                                    }
                                } catch (NumberFormatException e) {
                                    log.debug("⏭️  Number format error: {}", numericValue);
                                }
                            }
                        }
                    }
                }
            }

            log.info("❌ No '기부금' data found in report {}", rceptNo);
            return null;

        } catch (Exception e) {
            log.error("❌ Error extracting donation", e);
            return null;
        }
    }


    private Organization saveOrUpdateOrganization(String corpName, String stockCode) {
        return organizationRepository.findAll().stream()
                .filter(o -> o.getName().equals(corpName))
                .findFirst()
                .orElseGet(() -> {
                    Organization org = new Organization();
                    org.setName(corpName);
                    org.setType("상장사");
                    return organizationRepository.save(org);
                });
    }

    private void saveKpiReport(Organization org, BigDecimal amount, int year) {
        String projectName = org.getName() + " CSR " + year;
        Project project = projectRepository.findAll().stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setOrganization(org);
                    p.setName(projectName);
                    p.setCategory("CSR");
                    p.setStartDate(LocalDate.of(year, 1, 1));
                    return projectRepository.save(p);
                });

        Kpi donationKpi = kpiRepository.findByName("Donation Amount")
                .orElseGet(() -> {
                    Kpi kpi = new Kpi();
                    kpi.setName("Donation Amount");
                    kpi.setUnit("원");
                    kpi.setCategory("Finance");
                    return kpiRepository.save(kpi);
                });

        KpiReport report = new KpiReport();
        report.setProject(project);
        report.setKpi(donationKpi);
        report.setValue(amount);
        report.setReportDate(LocalDate.of(year, 12, 31));
        report.setStatus(ReportStatus.APPROVED);
        report.setApprovedBy("DART_AUTO");

        kpiReportRepository.save(report);
    }

    public void collectAllListedCompanies() {
        if (isCollecting) {
            log.warn("⚠️  Data collection is already in progress!");
            return;
        }

        isCollecting = true;
        startTime = System.currentTimeMillis();
        processedCompanies.set(0);
        successCount.set(0);
        failureCount.set(0);

        log.info("🚀 Starting parallel data collection...");
        log.info("⚙️  Parallelism: {} threads", parallelism);

        List<String> corpCodes = fetchAllCorpCodes();

        // ⭐⭐⭐ 여기에 추가 ⭐⭐⭐
        int SKIP_COUNT = 1615;
        if (corpCodes.size() > SKIP_COUNT) {
            log.info("⏩ Skipping first {} companies (already processed)", SKIP_COUNT);
            corpCodes = corpCodes.subList(SKIP_COUNT, corpCodes.size());
        }
        // ⭐⭐⭐ 여기까지 ⭐⭐⭐

        totalCompanies.set(corpCodes.size());

        log.info("📊 Total companies to process: {}", totalCompanies.get());

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String corpCode : corpCodes) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    collectDonationData(corpCode);
                    Thread.sleep(250);
                } catch (Exception e) {
                    log.error("Error processing corp: {}", corpCode, e);
                }
            }, executor);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    long duration = (System.currentTimeMillis() - startTime) / 1000;
                    log.info("═══════════════════════════════════════════════");
                    log.info("✅ Data Collection Completed!");
                    log.info("📊 Total: {} | Success: {} | Failed: {}",
                            totalCompanies.get(), successCount.get(), failureCount.get());
                    log.info("⏱️  Total time: {} seconds ({} minutes)", duration, duration / 60);
                    log.info("═══════════════════════════════════════════════");
                    isCollecting = false;
                    executor.shutdown();
                })
                .join();
    }

    public double getProgressPercentage() {
        if (totalCompanies.get() == 0) return 0.0;
        return (processedCompanies.get() * 100.0) / totalCompanies.get();
    }

    public long getEstimatedTimeRemaining() {
        if (!isCollecting || processedCompanies.get() == 0) return 0;

        long elapsed = System.currentTimeMillis() - startTime;
        int remaining = totalCompanies.get() - processedCompanies.get();
        long avgTimePerCompany = elapsed / processedCompanies.get();

        return (avgTimePerCompany * remaining) / 1000;
    }
}
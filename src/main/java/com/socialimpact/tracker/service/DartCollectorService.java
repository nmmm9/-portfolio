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
    /**
     * 전체 상장사 기부금 수집
     */
    public void collectAllListedCompanies() {
        if (isCollecting) {
            log.warn("⚠️ 이미 수집 작업이 진행 중입니다!");
            return;
        }

        isCollecting = true;
        startTime = System.currentTimeMillis();
        totalCompanies.set(0);
        processedCompanies.set(0);
        successCount.set(0);
        failureCount.set(0);

        try {
            log.info("🚀 전체 상장사 기부금 수집 시작");

            List<String> corpCodes = fetchAllCorpCodes();
            totalCompanies.set(corpCodes.size());

            for (String corpCode : corpCodes) {
                collectDonationData(corpCode);
                Thread.sleep(1000);
            }

            log.info("✅ 수집 완료! 성공: {}, 실패: {}", successCount.get(), failureCount.get());

        } catch (Exception e) {
            log.error("❌ 수집 작업 중 오류", e);
        } finally {
            isCollecting = false;
        }
    }

    /**
     * 특정 회사 기부금 수집
     */
    @Transactional
    public void collectDonationData(String corpCode) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            String companyJson = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/company.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", corpCode)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode companyInfo = mapper.readTree(companyJson);

            if (!"000".equals(companyInfo.path("status").asText())) {
                failureCount.incrementAndGet();
                return;
            }

            String corpName = companyInfo.path("corp_name").asText();
            log.info("[{}/{}] 처리 중: {}", processedCompanies.get() + 1, totalCompanies.get(), corpName);

            Organization org = saveOrUpdateOrganization(corpName, companyInfo.path("stock_code").asText());

            boolean hasData = false;
            for (int year = fromYear; year <= toYear; year++) {
                // 여기서 실제 기부금 수집 로직 (나중에 구현)
                // 지금은 일단 구조만
            }

            if (hasData) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }

        } catch (Exception e) {
            log.error("❌ 처리 중 오류: {}", corpCode, e);
            failureCount.incrementAndGet();
        } finally {
            processedCompanies.incrementAndGet();
        }
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
                    p.setEndDate(LocalDate.of(year, 12, 31));
                    return projectRepository.save(p);
                });

        Kpi kpi = kpiRepository.findByName("Donation Amount")
                .orElseGet(() -> {
                    Kpi newKpi = new Kpi();
                    newKpi.setName("Donation Amount");
                    newKpi.setUnit("원");
                    newKpi.setCategory("Finance");
                    return kpiRepository.save(newKpi);
                });

        KpiReport report = new KpiReport();
        report.setProject(project);
        report.setKpi(kpi);
        report.setValue(amount);
        report.setReportDate(LocalDate.of(year, 12, 31));
        report.setStatus(ReportStatus.APPROVED);
        report.setApprovedBy("DART_AUTO");

        kpiReportRepository.save(report);
    }
}

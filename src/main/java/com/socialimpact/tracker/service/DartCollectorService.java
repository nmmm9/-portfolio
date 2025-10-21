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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final DonationRepository donationRepository;



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
            processedCompanies.incrementAndGet();

            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            // 1. 회사 정보
            String companyJson = webClient.get()
                    .uri(ub -> ub.path("/api/company.json")
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
            String stockCode = companyInfo.path("stock_code").asText();

            // 2. Organization 자동 생성
            Organization org = organizationRepository.findAll().stream()
                    .filter(o -> o.getName().equals(corpName))
                    .findFirst()
                    .orElseGet(() -> {
                        Organization newOrg = new Organization();
                        newOrg.setName(corpName);
                        newOrg.setType("상장사");
                        return organizationRepository.save(newOrg);
                    });

            boolean foundData = false;

            // 3. 단일회사 재무제표 API로 기부금 조회
            for (int year = fromYear; year <= toYear; year++) {
                final int currentYear = year; // 람다용 final 변수
                try {
                    String fnlttJson = webClient.get()
                            .uri(ub -> ub.path("/api/fnlttSinglAcnt.json")
                                    .queryParam("crtfc_key", dartApiKey)
                                    .queryParam("corp_code", corpCode)
                                    .queryParam("bsns_year", String.valueOf(currentYear))
                                    .queryParam("reprt_code", "11011") // 사업보고서
                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    JsonNode fnlttData = mapper.readTree(fnlttJson);
                    if (!"000".equals(fnlttData.path("status").asText())) {
                        log.trace("  {}년 API 상태: {}", currentYear, fnlttData.path("message").asText());
                        continue;
                    }

                    JsonNode list = fnlttData.path("list");
                    if (!list.isArray() || list.size() == 0) {
                        log.trace("  {}년 데이터 없음", currentYear);
                        continue;
                    }

                    // 4. "기부금" 항목 찾기
                    for (JsonNode item : list) {
                        String accountNm = item.path("account_nm").asText();
                        String accountId = item.path("account_id").asText();

                        // dart_Donations 또는 항목명에 "기부금" 포함
                        if (accountId.equals("dart_Donations") || accountNm.contains("기부금")) {
                            // 당기 금액 우선, 없으면 전기
                            String amountStr = item.path("thstrm_amount").asText();
                            if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
                                amountStr = item.path("frmtrm_amount").asText();
                            }
                            if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
                                amountStr = item.path("bfefrmtrm_amount").asText(); // 전전기
                            }

                            if (amountStr != null && !amountStr.isEmpty()) {
                                amountStr = amountStr.replaceAll("[^0-9-]", "");

                                if (!amountStr.isEmpty() && !amountStr.equals("-")) {
                                    BigDecimal amount = new BigDecimal(amountStr);

                                    if (amount.compareTo(BigDecimal.ZERO) > 0) {
                                        Donation donation = donationRepository
                                                .findByOrganization_IdAndYearAndQuarter(org.getId(), currentYear, null)
                                                .orElse(new Donation());

                                        donation.setOrganization(org);
                                        donation.setOrganizationName(corpName);
                                        donation.setStockCode(stockCode);
                                        donation.setYear(currentYear);
                                        donation.setQuarter(null);
                                        donation.setDonationAmount(amount);
                                        donation.setDataSource("DART_API");
                                        donation.setReportType("사업보고서");
                                        donation.setVerificationStatus("자동수집");

                                        donationRepository.save(donation);
                                        foundData = true;

                                        log.info("  ✅ {} {}년: {} 원 ({})",
                                                corpName, currentYear, String.format("%,d", amount), accountNm);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    Thread.sleep(200); // API 제한 방지

                } catch (Exception e) {
                    log.trace("  {}년 실패", currentYear);
                }
            }

            if (foundData) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }

        } catch (Exception e) {
            failureCount.incrementAndGet();
        }
    }

    private BigDecimal parseXbrlForDonation(byte[] zipData) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                // 재무제표 XML 파일만 처리
                if (!entry.getName().endsWith(".xml")) continue;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String xmlContent = baos.toString("UTF-8");

                // 기부금 관련 태그 검색
                // XBRL 표준 태그: tuple_eng_donatio_expendtur_crt_trm_amount
                if (xmlContent.contains("기부금") || xmlContent.contains("donatio")) {
                    Document doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser());

                    // 기부금 금액 추출 (여러 패턴 시도)
                    String[] searchTags = {
                            "tuple_eng_donatio_expendtur_crt_trm_amount", // 당기 기부금
                            "donatio_expendtur_crt_trm_amount",
                            "donation_amount"
                    };

                    for (String tag : searchTags) {
                        Elements elements = doc.select(tag);
                        if (!elements.isEmpty()) {
                            String amountText = elements.first().text().replaceAll("[^0-9]", "");
                            if (!amountText.isEmpty()) {
                                return new BigDecimal(amountText);
                            }
                        }
                    }

                    // 태그로 못 찾으면 텍스트 패턴 검색
                    String[] patterns = {"기부금.*?([0-9,]+)", "donatio.*?([0-9,]+)"};
                    for (String pattern : patterns) {
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                        java.util.regex.Matcher m = p.matcher(xmlContent);
                        if (m.find()) {
                            String amountText = m.group(1).replaceAll(",", "");
                            return new BigDecimal(amountText);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("XBRL 파싱 중 오류", e);
        }

        return null;
    }

    // API 테스트용 엔드포인트 추가
    @GetMapping("/test-dart")
    public ResponseEntity<Map<String, Object>> testDartConnection() {
        try {
            // 1. corpCodes.xml 다운로드 테스트
            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();
            String corpCodesXml = webClient.get()
                    .uri(ub -> ub.path("/api/corpCode.xml")
                            .queryParam("crtfc_key", dartApiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 2. 삼성전자(005930) 테스트
            String testCorpCode = "00126380"; // 삼성전자
            String companyJson = webClient.get()
                    .uri(ub -> ub.path("/api/company.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", testCorpCode)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "corpCodesAvailable", corpCodesXml != null && !corpCodesXml.isEmpty(),
                    "samsungData", companyJson,
                    "message", "DART API 연결 정상"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
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

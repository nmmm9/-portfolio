package com.socialimpact.tracker.service;

import com.socialimpact.tracker.entity.Donation;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.DonationRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DonationCollectorService {

    private final OrganizationRepository organizationRepository;
    private final DonationRepository donationRepository;

    private Map<String, Organization> orgCache = null;

    /**
     * 여러 CSV 파일을 한 번에 처리
     */
    @Transactional
    public Map<String, Object> processDonationFiles(List<MultipartFile> files) {
        log.info("🚀 Starting donation CSV files processing... Total files: {}", files.size());

        loadOrganizationCache();

        int totalFiles = files.size();
        int totalRows = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedNoOrg = 0;
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                log.info("📄 Processing file: {}", file.getOriginalFilename());

                // 파일명에서 연도 추출 (예: "기부금_2023.csv" -> 2023)
                Integer fileYear = extractYearFromFilename(file.getOriginalFilename());

                Map<String, Object> result = processSingleCsvFile(file, fileYear);

                totalRows += (int) result.get("totalRows");
                successCount += (int) result.get("successCount");
                failureCount += (int) result.get("failureCount");
                skippedNoOrg += (int) result.get("skippedNoOrg");

            } catch (Exception e) {
                log.error("❌ Error processing file: {}", file.getOriginalFilename(), e);
                errors.add(String.format("File %s: %s", file.getOriginalFilename(), e.getMessage()));
            }
        }

        log.info("✅ All files processed! Total: {}, Success: {}, Failed: {}, Skipped: {}",
                totalRows, successCount, failureCount, skippedNoOrg);

        return Map.of(
                "totalFiles", totalFiles,
                "totalRows", totalRows,
                "successCount", successCount,
                "failureCount", failureCount,
                "skippedNoOrganization", skippedNoOrg,
                "errors", errors
        );
    }

    /**
     * 단일 CSV 파일 처리
     */
    @Transactional
    public Map<String, Object> processSingleCsvFile(MultipartFile file, Integer defaultYear) {
        int totalRows = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedNoOrg = 0;
        List<String> errors = new ArrayList<>();

        loadOrganizationCache();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build());

            for (CSVRecord record : csvParser) {
                totalRows++;

                try {
                    // "기부금" 항목만 필터링
                    String itemName = getColumnValue(record, "항목명");
                    if (itemName == null || !itemName.contains("기부금")) {
                        continue; // 기부금이 아니면 스킵
                    }

                    String corpName = getColumnValue(record, "회사명");
                    String stockCode = getColumnValue(record, "종목코드");

                    // Organizations 테이블에서 회사 찾기
                    Organization org = findOrganization(corpName, stockCode);
                    if (org == null) {
                        skippedNoOrg++;
                        continue; // 등록된 회사가 아니면 스킵
                    }

                    // 기부금 데이터 추출
                    String reportType = getColumnValue(record, "보고서종류");
                    String fiscalMonth = getColumnValue(record, "결산월");
                    String fiscalDate = getColumnValue(record, "결산기준일");

                    // 연도와 분기 추출
                    Integer year = extractYear(fiscalDate, defaultYear);
                    Integer quarter = extractQuarter(reportType, fiscalDate);

                    // 기부금 금액 추출 (당기 누적 우선, 없으면 당기 사용)
                    BigDecimal amount = extractDonationAmount(record);

                    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        continue; // 금액이 없거나 0이면 스킵
                    }

                    // Donation 엔티티 생성 또는 업데이트
                    Donation donation = donationRepository
                            .findByOrganizationIdAndYearAndQuarter(org.getId(), year, quarter)
                            .orElse(new Donation());

                    if (donation.getId() == null) {
                        // 신규 생성
                        donation.setOrganization(org);
                        donation.setOrganizationName(org.getName());
                        donation.setStockCode(stockCode);
                        donation.setYear(year);
                        donation.setQuarter(quarter);
                        donation.setDonationAmount(amount);
                        donation.setCurrency(getColumnValue(record, "통화"));
                        donation.setReportType(reportType);
                        donation.setFiscalMonth(parseFiscalMonth(fiscalMonth));
                        donation.setDataSource("CSV_" + year);
                        donation.setVerificationStatus("자동수집");
                    } else {
                        // 기존 데이터 업데이트 (더 큰 금액으로)
                        if (amount.compareTo(donation.getDonationAmount()) > 0) {
                            donation.setDonationAmount(amount);
                        }
                    }

                    donationRepository.save(donation);
                    successCount++;

                    if (successCount % 100 == 0) {
                        log.info("📊 Processed {} donations...", successCount);
                    }

                } catch (Exception e) {
                    failureCount++;
                    String error = String.format("Row %d: %s", totalRows, e.getMessage());
                    errors.add(error);
                    log.warn("⚠️  {}", error);
                }
            }

        } catch (IOException e) {
            log.error("❌ Error reading CSV file", e);
            throw new RuntimeException("Failed to process CSV file: " + e.getMessage());
        }

        return Map.of(
                "totalRows", totalRows,
                "successCount", successCount,
                "failureCount", failureCount,
                "skippedNoOrg", skippedNoOrg,
                "errors", errors
        );
    }

    /**
     * Organizations 캐시 로드 (GirCollectorService와 동일한 패턴)
     */
    private void loadOrganizationCache() {
        if (orgCache != null) {
            return;
        }

        log.info("📋 Loading all organizations into cache...");
        orgCache = new HashMap<>();

        List<Organization> allOrgs = organizationRepository.findAll();

        for (Organization org : allOrgs) {
            // 원본 이름으로 저장
            orgCache.put(org.getName(), org);

            // 정규화된 이름으로도 저장
            String normalized = normalizeCompanyName(org.getName());
            orgCache.put(normalized, org);
        }

        log.info("✅ Cached {} organizations ({} entries)", allOrgs.size(), orgCache.size());
    }

    /**
     * 회사 찾기 (회사명 또는 종목코드로)
     */
    private Organization findOrganization(String corpName, String stockCode) {
        if (corpName == null || corpName.isEmpty()) {
            return null;
        }

        // 1. 정확히 일치
        if (orgCache.containsKey(corpName)) {
            return orgCache.get(corpName);
        }

        // 2. 정규화 후 매칭
        String normalized = normalizeCompanyName(corpName);
        if (orgCache.containsKey(normalized)) {
            return orgCache.get(normalized);
        }

        // 3. 부분 매칭
        for (Map.Entry<String, Organization> entry : orgCache.entrySet()) {
            String cachedName = entry.getKey();
            if (normalized.length() >= 3 && cachedName.length() >= 3) {
                if (normalized.contains(cachedName) || cachedName.contains(normalized)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 회사명 정규화 (GirCollectorService와 동일)
     */
    private String normalizeCompanyName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String normalized = name;

        normalized = normalized.replaceAll("주식회사", "")
                .replaceAll("\\(주\\)", "")
                .replaceAll("㈜", "")
                .replaceAll("유한회사", "")
                .replaceAll("\\(유\\)", "");

        normalized = normalized.replace("SK", "에스케이")
                .replace("LG", "엘지")
                .replace("KT", "케이티")
                .replace("GS", "지에스")
                .replace("CJ", "씨제이")
                .replace("KB", "케이비")
                .replace("POSCO", "포스코")
                .replace("Posco", "포스코");

        normalized = normalized.replaceAll("[\\s()\\-_.,\\[\\]]", "")
                .toLowerCase();

        return normalized;
    }

    /**
     * CSV 컬럼 값 가져오기
     */
    private String getColumnValue(CSVRecord record, String columnName) {
        try {
            return record.get(columnName).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 파일명에서 연도 추출
     */
    private Integer extractYearFromFilename(String filename) {
        try {
            // "기부금_2023.csv" 또는 "2023_기부금.csv" 형식에서 연도 추출
            String[] parts = filename.replaceAll("[^0-9]", " ").trim().split("\\s+");
            for (String part : parts) {
                if (part.length() == 4 && part.startsWith("20")) {
                    return Integer.parseInt(part);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️  Could not extract year from filename: {}", filename);
        }
        return null;
    }

    /**
     * 결산기준일에서 연도 추출
     */
    private Integer extractYear(String fiscalDate, Integer defaultYear) {
        if (fiscalDate != null && fiscalDate.length() >= 4) {
            try {
                return Integer.parseInt(fiscalDate.substring(0, 4));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return defaultYear != null ? defaultYear : 2024;
    }

    /**
     * 보고서 종류와 결산기준일에서 분기 추출
     */
    private Integer extractQuarter(String reportType, String fiscalDate) {
        if (reportType != null) {
            if (reportType.contains("1분기")) return 1;
            if (reportType.contains("반기") || reportType.contains("2분기")) return 2;
            if (reportType.contains("3분기")) return 3;
            if (reportType.contains("사업보고서") || reportType.contains("4분기")) return 4;
        }

        // 결산기준일에서 월 추출
        if (fiscalDate != null && fiscalDate.length() >= 8) {
            try {
                int month = Integer.parseInt(fiscalDate.substring(4, 6));
                return (month - 1) / 3 + 1;
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return 4; // 기본값
    }

    /**
     * 기부금 금액 추출 (당기 누적 우선)
     */
    private BigDecimal extractDonationAmount(CSVRecord record) {
        String[] columns = {"당기 1분기 누적", "당기", "당기 1분기 3개월", "전기"};

        for (String col : columns) {
            try {
                String value = getColumnValue(record, col);
                if (value != null && !value.isEmpty()) {
                    String cleanValue = value.replaceAll("[^0-9.-]", "");
                    if (!cleanValue.isEmpty()) {
                        BigDecimal amount = new BigDecimal(cleanValue);
                        if (amount.compareTo(BigDecimal.ZERO) > 0) {
                            return amount;
                        }
                    }
                }
            } catch (Exception e) {
                // 다음 컬럼 시도
            }
        }

        return null;
    }

    /**
     * 결산월 파싱
     */
    private Integer parseFiscalMonth(String fiscalMonth) {
        if (fiscalMonth == null || fiscalMonth.isEmpty()) {
            return 12;
        }

        try {
            return Integer.parseInt(fiscalMonth.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 12;
        }
    }
}
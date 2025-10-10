package com.socialimpact.tracker.service;

import com.socialimpact.tracker.entity.Emission;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.EmissionRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GirCollectorService {

    private final OrganizationRepository organizationRepository;
    private final EmissionRepository emissionRepository;

    // Organizations 캐시
    private Map<String, Organization> orgCache = null;

    /**
     * GIR 엑셀 파일을 업로드하여 DB에 저장
     */
    @Transactional
    public Map<String, Object> processGirExcelFile(MultipartFile file) {
        log.info("🚀 Starting GIR Excel file processing...");

        // Organizations 캐싱
        loadOrganizationCache();

        int totalRows = 0;
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // 헤더 행 스킵
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // 헤더 스킵
            }

            // 데이터 처리
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                totalRows++;

                try {
                    processGirRow(row);
                    successCount++;

                    if (successCount % 100 == 0) {
                        log.info("📊 Processed {} rows...", successCount);
                    }
                } catch (Exception e) {
                    failureCount++;
                    String error = String.format("Row %d: %s", row.getRowNum(), e.getMessage());
                    errors.add(error);
                    log.warn("⚠️  {}", error);
                }
            }

            log.info("✅ GIR Excel processing completed!");
            log.info("📊 Total: {}, Success: {}, Failed: {}", totalRows, successCount, failureCount);

        } catch (IOException e) {
            log.error("❌ Error reading Excel file", e);
            throw new RuntimeException("Failed to process Excel file: " + e.getMessage());
        }

        return Map.of(
                "totalRows", totalRows,
                "successCount", successCount,
                "failureCount", failureCount,
                "errors", errors
        );
    }

    /**
     * Organizations 테이블 캐싱
     */
    private void loadOrganizationCache() {
        if (orgCache != null) {
            return; // 이미 로드됨
        }

        log.info("📋 Loading all organizations into cache...");
        orgCache = new HashMap<>();

        List<Organization> allOrgs = organizationRepository.findAll();

        for (Organization org : allOrgs) {
            if (!"상장사".equals(org.getType())) {
                continue;
            }

            // 원본 이름으로 저장
            orgCache.put(org.getName(), org);

            // 정규화된 이름으로도 저장
            String normalized = normalizeCompanyName(org.getName());
            orgCache.put(normalized, org);
        }

        log.info("✅ Cached {} organizations ({} entries)",
                allOrgs.stream().filter(o -> "상장사".equals(o.getType())).count(),
                orgCache.size());
    }

    /**
     * GIR 엑셀의 각 행을 처리
     */
    private void processGirRow(Row row) {
        // 컬럼 인덱스
        final int COL_CORP_NAME = 2;
        final int COL_YEAR = 3;
        final int COL_INDUSTRY = 5;
        final int COL_EMISSIONS = 6;
        final int COL_ENERGY = 7;

        // 데이터 추출
        String corpName = getCellValueAsString(row.getCell(COL_CORP_NAME)).trim();
        String yearStr = getCellValueAsString(row.getCell(COL_YEAR));
        String industry = getCellValueAsString(row.getCell(COL_INDUSTRY));
        String emissionsStr = getCellValueAsString(row.getCell(COL_EMISSIONS));
        String energyStr = getCellValueAsString(row.getCell(COL_ENERGY));

        if (corpName.isEmpty() || yearStr.isEmpty()) {
            throw new RuntimeException("필수 데이터 누락");
        }

        int year = Integer.parseInt(yearStr);
        BigDecimal emissions = parseNumericValue(emissionsStr);
        BigDecimal energy = parseNumericValue(energyStr);

        // Organization 찾기
        Organization org = findOrganization(corpName);

        // ⭐ 같은 회사+연도의 기존 레코드를 찾아서 합산
        Emission emission = emissionRepository
                .findByOrganizationIdAndYear(org.getId(), year)
                .orElse(new Emission());

        if (emission.getId() == null) {
            // 신규 레코드
            emission.setOrganization(org);
            emission.setOrganizationName(org.getName());
            emission.setGirCompanyName(corpName);
            emission.setYear(year);
            emission.setTotalEmissions(emissions);
            emission.setEnergyUsage(energy);
            emission.setIndustry(industry);
            emission.setDataSource("GIR");
            emission.setVerificationStatus("검증완료");
        } else {
            // ⭐ 기존 레코드에 합산 (같은 회사의 다른 사업장)
            emission.setTotalEmissions(emission.getTotalEmissions().add(emissions));
            emission.setEnergyUsage(emission.getEnergyUsage().add(energy));

            // GIR 법인명도 누적 (디버깅용)
            if (!emission.getGirCompanyName().contains(corpName)) {
                emission.setGirCompanyName(emission.getGirCompanyName() + ", " + corpName);
            }
        }

        emissionRepository.save(emission);
    }

    /**
     * Organizations 캐시에서 기업 찾기
     */
    private Organization findOrganization(String corpName) {
        // 1. 정확히 일치
        if (orgCache.containsKey(corpName)) {
            return orgCache.get(corpName);
        }

        // 2. 정규화 후 매칭
        String normalized = normalizeCompanyName(corpName);
        if (orgCache.containsKey(normalized)) {
            Organization org = orgCache.get(normalized);
            log.debug("🔗 Matched: '{}' → '{}'", corpName, org.getName());
            return org;
        }

        // 3. 부분 매칭
        for (Map.Entry<String, Organization> entry : orgCache.entrySet()) {
            String cachedName = entry.getKey();

            if (normalized.length() >= 3 && cachedName.length() >= 3) {
                if (normalized.contains(cachedName) || cachedName.contains(normalized)) {
                    Organization org = entry.getValue();
                    log.debug("🔗 Partial: '{}' → '{}'", corpName, org.getName());
                    return org;
                }
            }
        }

        // 4. 매칭 실패
        throw new RuntimeException("Organization not found: " + corpName);
    }

    /**
     * 회사명 정규화
     */
    private String normalizeCompanyName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String normalized = name;

        // 회사 형태 제거
        normalized = normalized.replaceAll("주식회사", "")
                .replaceAll("\\(주\\)", "")
                .replaceAll("㈜", "")
                .replaceAll("유한회사", "")
                .replaceAll("\\(유\\)", "");

        // 영문 → 한글
        normalized = normalized.replace("SK", "에스케이")
                .replace("LG", "엘지")
                .replace("KT", "케이티")
                .replace("GS", "지에스")
                .replace("CJ", "씨제이")
                .replace("KB", "케이비")
                .replace("POSCO", "포스코")
                .replace("Posco", "포스코");

        // 한글 → 영문
        normalized = normalized.replace("에스케이", "sk")
                .replace("엘지", "lg")
                .replace("케이티", "kt")
                .replace("지에스", "gs")
                .replace("씨제이", "cj")
                .replace("케이비", "kb")
                .replace("포스코", "posco");

        // 공백, 특수문자 제거 후 소문자
        normalized = normalized.replaceAll("[\\s()\\-_.,]", "")
                .toLowerCase();

        return normalized;
    }

    /**
     * 셀 값을 문자열로 변환
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    /**
     * 숫자 문자열을 BigDecimal로 변환
     */
    private BigDecimal parseNumericValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            String cleanValue = value.replaceAll(",", "");
            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            log.warn("⚠️  Failed to parse: {}", value);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 매칭 통계 조회
     */
    public Map<String, Object> getMatchingStatistics() {
        long totalOrgs = organizationRepository.findAll().stream()
                .filter(o -> "상장사".equals(o.getType()))
                .count();

        long totalEmissions = emissionRepository.count();

        long matchedOrgs = organizationRepository.findAll().stream()
                .filter(o -> "상장사".equals(o.getType()))
                .filter(org -> emissionRepository.findByOrganizationId(org.getId()).size() > 0)
                .count();

        return Map.of(
                "totalOrganizations", totalOrgs,
                "matchedOrganizations", matchedOrgs,
                "unmatchedOrganizations", totalOrgs - matchedOrgs,
                "totalEmissionRecords", totalEmissions,
                "matchRate", totalOrgs > 0 ? (matchedOrgs * 100.0 / totalOrgs) : 0.0
        );
    }
}
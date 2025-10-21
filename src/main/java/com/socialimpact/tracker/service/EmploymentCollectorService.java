package com.socialimpact.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialimpact.tracker.entity.Employment;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.repository.EmploymentRepository;
import com.socialimpact.tracker.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmploymentCollectorService {

    private final WebClient.Builder webClientBuilder;
    private final EmploymentRepository employmentRepository;
    private final OrganizationRepository organizationRepository;

    @Value("${opendart.api-key}")
    private String dartApiKey;

    @Value("${opendart.base-url}")
    private String dartBaseUrl;

    /**
     * 전체 상장사의 고용 현황 수집
     */
    @Transactional
    public void collectAllEmployments(int year) {
        log.info("🚀 Starting employment data collection for year {}", year);

        List<Organization> organizations = organizationRepository.findAll();
        log.info("📊 Found {} organizations to process", organizations.size());

        int successCount = 0;
        int failureCount = 0;
        int skipCount = 0;

        for (Organization org : organizations) {
            try {
                if (org.getCorpCode() == null || org.getCorpCode().isEmpty()) {
                    skipCount++;
                    continue;
                }

                boolean collected = collectEmploymentData(org, year);
                if (collected) {
                    successCount++;
                    if (successCount % 10 == 0) {
                        log.info("✅ Progress: {}/{} Success: {}",
                                successCount + failureCount + skipCount,
                                organizations.size(),
                                successCount);
                    }
                } else {
                    skipCount++;
                }

                // API 호출 제한 방지 (1초 대기)
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("❌ Error processing {}: {}", org.getName(), e.getMessage());
                failureCount++;
            }
        }

        log.info("✅ Collection completed! Success: {}, Failed: {}, Skipped: {}",
                successCount, failureCount, skipCount);
    }

    /**
     * 특정 기업의 고용 현황 수집
     */
    @Transactional
    public boolean collectEmploymentData(Organization org, int year) {
        try {
            // 이미 데이터가 있는지 확인
            Optional<Employment> existing = employmentRepository
                    .findByOrganization_IdAndYear(org.getId(), year);

            if (existing.isPresent()) {
                return false;
            }

            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            // DART API 호출: 임직원 현황 조회
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/empSttus.json")
                            .queryParam("crtfc_key", dartApiKey)
                            .queryParam("corp_code", org.getCorpCode())
                            .queryParam("bsns_year", year)
                            .queryParam("reprt_code", "11011") // 사업보고서
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                return false;
            }

            JsonNode root = mapper.readTree(response);

            // API 응답 상태 확인
            String status = root.path("status").asText();
            if (!"000".equals(status)) {
                if (!"013".equals(status)) { // 013은 데이터 없음 (정상)
                    log.debug("⚠️  API error for {}: {}", org.getName(), status);
                }
                return false;
            }

            JsonNode list = root.path("list");
            if (!list.isArray() || list.size() == 0) {
                return false;
            }

            // 첫 번째 레코드 사용
            JsonNode empData = list.get(0);

            // Employment 엔티티 생성
            Employment employment = new Employment();
            employment.setOrganization(org);
            employment.setOrganizationName(org.getName());
            employment.setStockCode(org.getStockCode());
            employment.setYear(year);

            // 데이터 파싱
            employment.setMaleEmployees(parseInteger(empData.path("fo_bbm").asText()));
            employment.setFemaleEmployees(parseInteger(empData.path("fo_bbw").asText()));
            employment.setTotalEmployees(parseInteger(empData.path("sm").asText()));
            employment.setAverageServiceYears(parseDouble(empData.path("avrg_cnwk_sdytrn").asText()));

            // 정규직 (상시근로자 수)
            Integer regular = parseInteger(empData.path("rgllbr_co").asText());
            if (regular == null) {
                regular = employment.getTotalEmployees();
            }
            employment.setRegularEmployees(regular);

            Integer total = employment.getTotalEmployees();
            if (total != null && regular != null) {
                employment.setContractEmployees(Math.max(0, total - regular));
            }

            employment.setDataSource("DART_API");
            employment.setVerificationStatus("자동수집");

            employmentRepository.save(employment);
            log.info("💾 Saved: {} ({}) - {} employees",
                    org.getName(), year, employment.getTotalEmployees());

            return true;

        } catch (Exception e) {
            log.error("❌ Failed: {}: {}", org.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 여러 연도의 데이터 한번에 수집
     */
    @Transactional
    public void collectMultipleYears(int fromYear, int toYear) {
        log.info("🚀 Starting multi-year employment data collection ({} - {})", fromYear, toYear);

        for (int year = fromYear; year <= toYear; year++) {
            log.info("📅 Collecting data for year {}", year);
            collectAllEmployments(year);
        }

        log.info("✅ Multi-year collection completed!");
    }

    // 유틸리티 메서드
    private Integer parseInteger(String value) {
        try {
            if (value == null || value.trim().isEmpty() || "-".equals(value)) {
                return null;
            }
            value = value.replace(",", "").trim();
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            if (value == null || value.trim().isEmpty() || "-".equals(value)) {
                return null;
            }
            value = value.replace(",", "").trim();
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
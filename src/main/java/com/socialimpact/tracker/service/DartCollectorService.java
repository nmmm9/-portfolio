package com.socialimpact.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.socialimpact.tracker.entity.*;
import com.socialimpact.tracker.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartCollectorService {

    private final WebClient.Builder webClientBuilder;
    private final OrganizationRepository organizationRepository;
    private final DonationRepository donationRepository;
    private final ApplicationContext applicationContext; // ← 추가

    @Value("${opendart.api-key}")
    private String dartApiKey;

    @Value("${opendart.base-url}")
    private String dartBaseUrl;

    @Value("${ingest.donation.from-year}")
    private int fromYear;

    @Value("${ingest.donation.to-year}")
    private int toYear;

    // 체크포인트 파일 경로
    private static final String CHECKPOINT_FILE = "donation_checkpoint.txt";
    private static final String PROGRESS_FILE = "donation_progress.log";

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

    private volatile boolean apiLimitReached = false;
    private int consecutiveApiErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    /**
     * 🔄 체크포인트 저장
     */
    private void saveCheckpoint(int currentIndex, String corpCode) {
        try {
            Path checkpointPath = Paths.get(CHECKPOINT_FILE);
            String data = currentIndex + "," + corpCode + "," + System.currentTimeMillis();
            Files.writeString(checkpointPath, data);
            log.info("💾 체크포인트 저장: {} ({})", currentIndex, corpCode);
        } catch (IOException e) {
            log.error("❌ 체크포인트 저장 실패", e);
        }
    }

    /**
     * 📖 체크포인트 로드
     */
    private Integer loadCheckpoint() {
        try {
            Path checkpointPath = Paths.get(CHECKPOINT_FILE);
            if (Files.exists(checkpointPath)) {
                String data = Files.readString(checkpointPath);
                String[] parts = data.split(",");
                if (parts.length >= 1) {
                    int index = Integer.parseInt(parts[0]);
                    log.info("📖 체크포인트 발견: {}번째 회사부터 재개", index);
                    return index;
                }
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("⚠️ 체크포인트 로드 실패, 처음부터 시작합니다");
        }
        return 0;
    }

    /**
     * 🗑️ 체크포인트 삭제 (완료 시)
     */
    private void deleteCheckpoint() {
        try {
            Path checkpointPath = Paths.get(CHECKPOINT_FILE);
            if (Files.exists(checkpointPath)) {
                Files.delete(checkpointPath);
                log.info("🗑️ 체크포인트 삭제 (수집 완료)");
            }
        } catch (IOException e) {
            log.error("❌ 체크포인트 삭제 실패", e);
        }
    }

    /**
     * 📊 진행 상황 로그 저장
     */
    private void saveProgressLog(String message) {
        try {
            Path progressPath = Paths.get(PROGRESS_FILE);
            String timestamp = java.time.LocalDateTime.now().toString();
            String logEntry = String.format("[%s] %s\n", timestamp, message);
            Files.writeString(progressPath, logEntry,
                    Files.exists(progressPath)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            log.debug("진행 로그 저장 실패", e);
        }
    }

    /**
     * 🚨 API 제한 감지
     */
    private boolean checkApiLimit(WebClientResponseException e) {
        String errorBody = e.getResponseBodyAsString();
        boolean isLimitError = e.getStatusCode().is4xxClientError()
                || errorBody.contains("LIMITED_NUMBER_OF_SERVICE")
                || errorBody.contains("SERVICE KEY IS NOT REGISTERED")
                || errorBody.contains("API_LIMIT_EXCEEDED")
                || errorBody.contains("NORMAL SERVICE");

        if (isLimitError) {
            consecutiveApiErrors++;
            log.warn("⚠️ API 오류 감지 ({}회 연속): {}", consecutiveApiErrors, errorBody);

            if (consecutiveApiErrors >= MAX_CONSECUTIVE_ERRORS) {
                apiLimitReached = true;
                log.error("🚫 API 제한 도달! {} 회 연속 오류", consecutiveApiErrors);
                return true;
            }
        } else {
            consecutiveApiErrors = 0; // 정상 응답 시 리셋
        }

        return false;
    }

    /**
     * 🛑 서버 우아한 종료
     */
    private void shutdownGracefully() {
        log.info("🛑 API 제한으로 인한 서버 종료 시작...");
        log.info("📊 최종 통계:");
        log.info("   - 총 처리: {} / {}", processedCompanies.get(), totalCompanies.get());
        log.info("   - 성공: {}", successCount.get());
        log.info("   - 실패: {}", failureCount.get());
        log.info("   - 진행률: {:.2f}%", getProgressPercentage());

        saveProgressLog(String.format(
                "API 제한 도달 - 처리: %d/%d, 성공: %d, 실패: %d",
                processedCompanies.get(), totalCompanies.get(),
                successCount.get(), failureCount.get()
        ));

        // 5초 후 서버 종료
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("👋 서버 종료 중...");
                System.exit(0); // 강제 종료
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 📋 모든 상장사 corp_code 가져오기
     */
    public List<String> fetchAllCorpCodes() {
        log.info("📥 DART에서 상장사 목록 가져오기...");

        List<String> corpCodes = new ArrayList<>();

        try {
            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();

            byte[] zipData = webClient.get()
                    .uri(ub -> ub.path("/corpCode.xml")
                            .queryParam("crtfc_key", dartApiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (zipData == null) {
                log.error("❌ corpCode.xml 다운로드 실패");
                return corpCodes;
            }

            // ZIP 압축 해제
            XmlMapper xmlMapper = new XmlMapper();
            try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("CORPCODE.xml")) {
                        byte[] xmlData = zis.readAllBytes();
                        String xmlContent = new String(xmlData, "UTF-8");

                        JsonNode root = xmlMapper.readTree(xmlContent);
                        JsonNode list = root.path("list");

                        if (list.isArray()) {
                            for (JsonNode item : list) {
                                String corpCode = item.path("corp_code").asText().trim();
                                String stockCode = item.has("stock_code")
                                        ? item.get("stock_code").asText().trim()
                                        : "";

                                // 상장사만 필터링 (stock_code가 6자리 숫자)
                                if (!stockCode.isEmpty() && stockCode.matches("\\d{6}")) {
                                    corpCodes.add(corpCode);
                                }
                            }
                            log.info("✅ 총 {}개 아이템 중 {}개 상장사 발견", list.size(), corpCodes.size());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ corpCode 가져오기 실패", e);
        }

        return corpCodes;
    }

    /**
     * 🚀 전체 상장사 기부금 수집 (체크포인트 지원)
     */
    public void collectAllListedCompanies() {
        if (isCollecting) {
            log.warn("⚠️ 이미 수집 작업이 진행 중입니다!");
            return;
        }

        isCollecting = true;
        apiLimitReached = false;
        consecutiveApiErrors = 0;
        startTime = System.currentTimeMillis();

        try {
            log.info("🚀 기부금 수집 시작");

            List<String> corpCodes = fetchAllCorpCodes();
            totalCompanies.set(corpCodes.size());

            // 체크포인트 로드
            Integer startIndex = loadCheckpoint();
            if (startIndex > 0) {
                log.info("🔄 {}번째 회사부터 재개합니다", startIndex);
                processedCompanies.set(startIndex);
            } else {
                processedCompanies.set(0);
                successCount.set(0);
                failureCount.set(0);
            }

            // 진행 상황 로그 초기화
            if (startIndex == 0) {
                saveProgressLog("=== 새로운 수집 시작 ===");
            } else {
                saveProgressLog(String.format("=== 수집 재개 (%d번째부터) ===", startIndex));
            }

            // 수집 시작
            for (int i = startIndex; i < corpCodes.size(); i++) {
                String corpCode = corpCodes.get(i);

                // API 제한 체크
                if (apiLimitReached) {
                    log.error("🚫 API 제한 도달! 수집 중단");
                    saveCheckpoint(i, corpCode);
                    saveProgressLog(String.format("API 제한 - %d번째에서 중단", i));
                    shutdownGracefully();
                    return;
                }

                // 기부금 데이터 수집
                boolean success = collectDonationData(corpCode, i);

                // 진행 상황 로그 (10개마다)
                if (i % 10 == 0 || !success) {
                    double progress = getProgressPercentage();
                    long remaining = getEstimatedTimeRemaining();

                    String progressBar = createProgressBar(progress);
                    log.info("\n{} {:.0f}%\n       처리:{}/{} | 수집:{}건(+{}) | {}",
                            progressBar, progress,
                            processedCompanies.get(), totalCompanies.get(),
                            successCount.get(), success ? 1 : 0,
                            formatTime(remaining)
                    );
                }

                // 체크포인트 저장 (100개마다)
                if (i % 100 == 0 && i > 0) {
                    saveCheckpoint(i, corpCode);
                }

                // API 호출 제한 방지
                Thread.sleep(1000);
            }

            // 수집 완료
            deleteCheckpoint();
            log.info("✅ 전체 수집 완료! 성공: {}, 실패: {}",
                    successCount.get(), failureCount.get());
            saveProgressLog(String.format(
                    "수집 완료 - 성공: %d, 실패: %d",
                    successCount.get(), failureCount.get()
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ 수집 작업 중단됨");
            saveProgressLog("수집 작업 강제 중단");
        } catch (Exception e) {
            log.error("❌ 수집 작업 중 오류", e);
            saveProgressLog("오류 발생: " + e.getMessage());
        } finally {
            isCollecting = false;
        }
    }

    /**
     * 💰 특정 회사 기부금 수집
     */
    @Transactional
    public boolean collectDonationData(String corpCode, int currentIndex) {
        try {
            processedCompanies.incrementAndGet();

            WebClient webClient = webClientBuilder.baseUrl(dartBaseUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            // 1. 회사 정보 조회
            String companyJson;
            try {
                companyJson = webClient.get()
                        .uri(ub -> ub.path("/api/company.json")
                                .queryParam("crtfc_key", dartApiKey)
                                .queryParam("corp_code", corpCode)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException e) {
                if (checkApiLimit(e)) {
                    saveCheckpoint(currentIndex, corpCode);
                    return false;
                }
                throw e;
            }

            JsonNode companyInfo = mapper.readTree(companyJson);
            if (!"000".equals(companyInfo.path("status").asText())) {
                failureCount.incrementAndGet();
                return false;
            }

            String corpName = companyInfo.path("corp_name").asText();
            String stockCode = companyInfo.path("stock_code").asText();

            // 2. Organization 찾기 또는 생성
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

            // 3. 연도별 기부금 데이터 수집
            for (int year = fromYear; year <= toYear; year++) {
                final int currentYear = year;

                try {
                    String fnlttJson;
                    try {
                        fnlttJson = webClient.get()
                                .uri(ub -> ub.path("/api/fnlttSinglAcnt.json")
                                        .queryParam("crtfc_key", dartApiKey)
                                        .queryParam("corp_code", corpCode)
                                        .queryParam("bsns_year", String.valueOf(currentYear))
                                        .queryParam("reprt_code", "11011") // 사업보고서
                                        .build())
                                .retrieve()
                                .bodyToMono(String.class)
                                .block();
                    } catch (WebClientResponseException e) {
                        if (checkApiLimit(e)) {
                            saveCheckpoint(currentIndex, corpCode);
                            return false;
                        }
                        continue;
                    }

                    JsonNode fnlttData = mapper.readTree(fnlttJson);
                    if (!"000".equals(fnlttData.path("status").asText())) {
                        continue;
                    }

                    JsonNode list = fnlttData.path("list");
                    if (!list.isArray() || list.size() == 0) {
                        continue;
                    }

                    // 4. 기부금 항목 찾기
                    for (JsonNode item : list) {
                        String accountNm = item.path("account_nm").asText();
                        String accountId = item.path("account_id").asText();

                        if (accountId.equals("dart_Donations") || accountNm.contains("기부금")) {
                            // 금액 추출
                            String amountStr = item.path("thstrm_amount").asText();
                            if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
                                amountStr = item.path("frmtrm_amount").asText();
                            }
                            if (amountStr == null || amountStr.isEmpty() || amountStr.equals("-")) {
                                continue;
                            }

                            amountStr = amountStr.replaceAll("[^0-9-]", "");
                            if (amountStr.isEmpty() || amountStr.equals("-")) {
                                continue;
                            }

                            BigDecimal amount = new BigDecimal(amountStr);
                            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                continue;
                            }

                            // 기부금 저장
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

                            log.debug("  ✅ {} {}년: {} 원", corpName, currentYear,
                                    String.format("%,d", amount));
                            break;
                        }
                    }

                    Thread.sleep(200); // API 제한 방지

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    log.trace("  {}년 처리 실패", currentYear);
                }
            }

            if (foundData) {
                successCount.incrementAndGet();
                consecutiveApiErrors = 0; // 성공 시 에러 카운트 리셋
                return true;
            } else {
                failureCount.incrementAndGet();
                return false;
            }

        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.trace("회사 수집 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 📊 진행률 계산
     */
    public double getProgressPercentage() {
        if (totalCompanies.get() == 0) return 0.0;
        return (processedCompanies.get() * 100.0) / totalCompanies.get();
    }

    /**
     * ⏱️ 예상 남은 시간 계산 (초)
     */
    public long getEstimatedTimeRemaining() {
        if (!isCollecting || processedCompanies.get() == 0) return 0;

        long elapsed = System.currentTimeMillis() - startTime;
        int remaining = totalCompanies.get() - processedCompanies.get();
        long avgTimePerCompany = elapsed / processedCompanies.get();

        return (avgTimePerCompany * remaining) / 1000;
    }

    /**
     * 🎨 진행 바 생성
     */
    private String createProgressBar(double percentage) {
        int barLength = 50;
        int filled = (int) (barLength * percentage / 100);
        StringBuilder bar = new StringBuilder("[");
        bar.append("█".repeat(Math.max(0, filled)));
        bar.append("░".repeat(Math.max(0, barLength - filled)));
        bar.append("]");
        return bar.toString();
    }

    /**
     * ⏰ 시간 포맷팅 (초 -> HH:MM:SS)
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
package com.socialimpact.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.entity.PositiveNews;
import com.socialimpact.tracker.repository.OrganizationRepository;
import com.socialimpact.tracker.repository.PositiveNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositiveNewsCollectorService {

    private final WebClient.Builder webClientBuilder;
    private final PositiveNewsRepository positiveNewsRepository;
    private final OrganizationRepository organizationRepository;

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.search-url}")
    private String searchUrl;

    @Value("${positive-news.display:100}")
    private int display;

    // 진행 상황 추적
    private final AtomicInteger totalOrgs = new AtomicInteger(0);
    private final AtomicInteger processedOrgs = new AtomicInteger(0);
    private final AtomicInteger totalCollectedNews = new AtomicInteger(0);
    private volatile boolean isCollecting = false;

    // 체크포인트 파일
    private static final String CHECKPOINT_FILE = "news_collection_checkpoint.txt";

    // 키워드 카테고리 매핑
    private static final Map<String, List<String>> POSITIVE_KEYWORD_CATEGORIES = Map.ofEntries(
            Map.entry("기부", Arrays.asList("기부", "후원", "기증", "장학금", "지원금", "성금", "모금", "전달식")),
            Map.entry("봉사", Arrays.asList("봉사", "재능기부", "사회공헌", "자원봉사", "나눔")),
            Map.entry("환경", Arrays.asList("친환경", "탄소중립", "재생에너지", "ESG", "녹색경영", "환경보호")),
            Map.entry("교육", Arrays.asList("교육지원", "멘토링", "장학생", "인재양성", "교육기부")),
            Map.entry("일자리", Arrays.asList("일자리창출", "채용확대", "신규채용", "청년고용", "정규직전환")),
            Map.entry("지역사회", Arrays.asList("지역사회", "상생협력", "MOU", "업무협약", "협약식")),
            Map.entry("윤리경영", Arrays.asList("윤리경영", "투명경영", "준법경영", "공정거래")),
            Map.entry("혁신", Arrays.asList("R&D투자", "기술개발", "혁신", "특허"))
    );

    // 부정 키워드
    private static final Set<String> NEGATIVE_KEYWORDS = new HashSet<>(Arrays.asList(
            "기소", "구속", "벌금", "과징금", "제재", "처벌", "징역", "실형", "법원", "재판", "소송",
            "고소", "고발", "수사", "검찰", "경찰", "횡령", "배임", "사기", "뇌물", "비리", "탈세",
            "적자", "손실", "부채", "파산", "회생", "구조조정", "감원", "해고", "정리해고", "희망퇴직",
            "사고", "화재", "폭발", "리콜", "결함", "불량", "오염", "파업", "태업", "쟁의",
            "논란", "비판", "질타", "반발", "항의", "의혹", "추정", "의심", "불투명",
            "청소원", "경비원", "사외이사", "이사회참석", "불참", "체력시험"
    ));

    // 무관한 키워드
    private static final Set<String> IRRELEVANT_KEYWORDS = new HashSet<>(Arrays.asList(
            "날씨", "교통", "부동산", "아파트", "축구", "야구", "드라마", "영화", "연예인", "맛집"
    ));

    // 종합뉴스 필터 (여러 회사가 나열된 뉴스 제외)
    private static final Set<String> SUMMARY_NEWS_KEYWORDS = new HashSet<>(Arrays.asList(
            "장 마감 후", "장마감후", "e공시", "공시 눈에 띄네", "주요공시", "주요 공시",
            "증권사 주요 공시", "오늘의 공시", "공시 요약"
    ));

    /**
     * 체크포인트 저장
     */
    private void saveCheckpoint(Long orgId) {
        try {
            FileWriter writer = new FileWriter(CHECKPOINT_FILE);
            writer.write(String.valueOf(orgId));
            writer.close();
            log.debug("📍 체크포인트 저장: {}", orgId);
        } catch (Exception e) {
            log.warn("체크포인트 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 체크포인트 로드
     */
    private Long loadCheckpoint() {
        try {
            File file = new File(CHECKPOINT_FILE);
            if (file.exists()) {
                String content = new String(Files.readAllBytes(Paths.get(CHECKPOINT_FILE)));
                return Long.parseLong(content.trim());
            }
        } catch (Exception e) {
            log.warn("체크포인트 로드 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 체크포인트 삭제
     */
    private void deleteCheckpoint() {
        try {
            new File(CHECKPOINT_FILE).delete();
            log.info("✅ 체크포인트 삭제");
        } catch (Exception e) {
            log.warn("체크포인트 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 전체 조직의 긍정 뉴스 수집 (자동 DB 초기화 포함)
     */
    public void collectAllPositiveNews(int fromYear, int toYear) {
        collectAllPositiveNews(fromYear, toYear, false); // 기본적으로 기존 뉴스 유지
    }

    /**
     * 전체 조직의 긍정 뉴스 수집
     * @param clearBeforeCollect true면 수집 전 기존 뉴스 삭제
     */
    public void collectAllPositiveNews(int fromYear, int toYear, boolean clearBeforeCollect) {
        if (isCollecting) {
            log.warn("⚠️ 이미 수집 작업이 진행 중입니다!");
            return;
        }

        isCollecting = true;
        long startTime = System.currentTimeMillis();

        try {
            log.info("🚀 긍정 뉴스 수집 시작 ({} - {})", fromYear, toYear);

            // 1. 기존 뉴스 삭제 (옵션)
            if (clearBeforeCollect) {
                clearAllNews();
                deleteCheckpoint();
            }

            // 2. 체크포인트 확인
            Long startFromId = loadCheckpoint();
            if (startFromId != null) {
                log.info("📍 체크포인트 발견: ID {} 부터 재시작", startFromId);
            }

            // 3. 전체 조직 조회
            List<Organization> allOrgs = organizationRepository.findAll();

            // startFromId 이후부터 필터링
            List<Organization> organizations;
            if (startFromId != null) {
                Long finalStartFromId = startFromId;
                organizations = allOrgs.stream()
                        .filter(org -> org.getId() >= finalStartFromId)
                        .collect(Collectors.toList());
                log.info("재시작: {} 개 조직 처리 (전체 {}개 중)", organizations.size(), allOrgs.size());
            } else {
                organizations = allOrgs;
            }

            totalOrgs.set(organizations.size());
            processedOrgs.set(0);
            totalCollectedNews.set(0);

            log.info("✅ 총 {} 개 조직에서 뉴스 수집", organizations.size());

            // 4. 각 조직의 긍정 뉴스 수집
            for (Organization org : organizations) {
                try {
                    int newsCount = collectPositiveNewsForOrganization(org, fromYear, toYear);
                    totalCollectedNews.addAndGet(newsCount);
                    processedOrgs.incrementAndGet();

                    // 진행률 로그 (뉴스 수집했을 때 항상 표시)
                    if (newsCount > 0) {
                        int progress = (int) (processedOrgs.get() * 100.0 / totalOrgs.get());
                        log.info("✅ [{}] {} 건 수집 | 진행: {}/{} ({}%) | 누적: {} 건",
                                org.getName(), newsCount,
                                processedOrgs.get(), totalOrgs.get(), progress,
                                totalCollectedNews.get());
                    } else if (processedOrgs.get() % 10 == 0) {
                        int progress = (int) (processedOrgs.get() * 100.0 / totalOrgs.get());
                        log.info("진행: {} / {} ({}%) | 누적 뉴스: {} 건",
                                processedOrgs.get(), totalOrgs.get(), progress, totalCollectedNews.get());
                    }

                    // 체크포인트 저장 (다음 회사 ID)
                    saveCheckpoint(org.getId() + 1);

                    Thread.sleep(100); // API 호출 제한

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("작업 중단됨");
                    break;
                } catch (RuntimeException e) {
                    // API 제한 에러면 중단
                    if (e.getMessage() != null && e.getMessage().contains("API_LIMIT_EXCEEDED")) {
                        log.error("🚫 API 할당량 초과로 중단됨");
                        log.info("📍 체크포인트 저장됨: {} 부터 재시작 가능", org.getName());
                        saveCheckpoint(org.getId()); // 현재 회사 ID 저장 (다음에 이 회사부터 재시작)
                        break;
                    }
                    log.error("❌ 회사 수집 실패 [{}]: {}", org.getName(), e.getMessage());
                } catch (Exception e) {
                    log.error("❌ 회사 수집 실패 [{}]: {}", org.getName(), e.getMessage());
                }
            }

            // 모든 수집 완료 시 체크포인트 삭제
            if (processedOrgs.get() >= organizations.size()) {
                deleteCheckpoint();
                log.info("✅ 전체 수집 완료!");
            } else {
                log.warn("⚠️ 일부만 완료됨 (체크포인트 유지)");
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("처리: {} 개 | 수집: {} 건 | 소요: {} 초",
                    processedOrgs.get(), totalCollectedNews.get(), elapsedTime / 1000);

        } catch (Exception e) {
            log.error("❌ 전체 수집 작업 중 오류", e);
        } finally {
            isCollecting = false;
        }
    }

    /**
     * 기존 뉴스 전체 삭제
     */
    @Transactional
    public void clearAllNews() {
        long count = positiveNewsRepository.count();
        if (count > 0) {
            log.info("🗑️ 기존 뉴스 삭제 중: {} 건", count);
            positiveNewsRepository.deleteAll();
            positiveNewsRepository.flush();
            log.info("✅ 삭제 완료");
        }
    }

    /**
     * 특정 조직의 긍정 뉴스 수집
     */
    @Transactional
    public int collectPositiveNewsForOrganization(Organization org, int fromYear, int toYear) {
        Set<String> processedUrls = ConcurrentHashMap.newKeySet();
        int totalCount = 0;

        // 전략 1: 키워드 카테고리별 검색
        for (Map.Entry<String, List<String>> entry : POSITIVE_KEYWORD_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                try {
                    String query = org.getName() + " " + keyword;
                    int count = searchAndSaveNews(org, query, category, keyword, fromYear, toYear, processedUrls);
                    totalCount += count;

                    if (count > 0) {
                        log.info("  ✓ [{}] {}: {} 건", org.getName(), keyword, count);
                    }

                    Thread.sleep(100); // API 호출 제한

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException e) {
                    // API 제한 에러면 상위로 전파
                    if (e.getMessage() != null && e.getMessage().contains("API_LIMIT_EXCEEDED")) {
                        throw e;
                    }
                    log.debug("⚠️ 검색 실패 [{} + {}]: {}", org.getName(), keyword, e.getMessage());
                } catch (Exception e) {
                    log.debug("⚠️ 검색 실패 [{} + {}]: {}", org.getName(), keyword, e.getMessage());
                }
            }
        }

        // 전략 2: 회사명만으로 검색 (추가 뉴스 발굴)
        try {
            int count = searchAndSaveNews(org, org.getName(), "전체", "전체", fromYear, toYear, processedUrls);
            totalCount += count;
            if (count > 0) {
                log.debug("  ✓ [{}] 회사명 단독 검색: {} 건", org.getName(), count);
            }
        } catch (Exception e) {
            log.debug("⚠️ 전체 검색 실패 [{}]: {}", org.getName(), e.getMessage());
        }

        return totalCount;
    }

    /**
     * 네이버 뉴스 검색 및 저장
     */
    private int searchAndSaveNews(Organization org, String query, String category,
                                  String keyword, int fromYear, int toYear,
                                  Set<String> processedUrls) {
        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(searchUrl)
                    .defaultHeader("X-Naver-Client-Id", clientId)
                    .defaultHeader("X-Naver-Client-Secret", clientSecret)
                    .build();

            ObjectMapper mapper = new ObjectMapper();

            // 네이버 검색 API 호출 (타임아웃 설정 추가)
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10)) // 타임아웃 10초
                    .onErrorResume(error -> {
                        String errorMsg = error.getMessage();
                        log.warn("API 호출 실패: {}", errorMsg);

                        // 429 에러 (API 제한)면 예외 던지기
                        if (errorMsg != null && errorMsg.contains("429")) {
                            log.error("🚫 API 할당량 초과! 프로그램 중단");
                            throw new RuntimeException("API_LIMIT_EXCEEDED: " + errorMsg);
                        }

                        return Mono.empty();
                    })
                    .block();

            if (response == null || response.isEmpty()) {
                return 0;
            }

            JsonNode root = mapper.readTree(response);
            JsonNode items = root.path("items");

            if (!items.isArray() || items.size() == 0) {
                return 0;
            }

            int savedCount = 0;

            for (JsonNode item : items) {
                try {
                    String title = cleanHtml(item.path("title").asText());
                    String description = cleanHtml(item.path("description").asText());
                    String link = item.path("link").asText();
                    String pubDate = item.path("pubDate").asText();

                    // 날짜 파싱 및 연도 필터링
                    LocalDate publishedDate = parseNaverDate(pubDate);
                    if (publishedDate == null ||
                            publishedDate.getYear() < fromYear ||
                            publishedDate.getYear() > toYear) {
                        continue;
                    }

                    // URL 중복 체크
                    if (processedUrls.contains(link) || positiveNewsRepository.existsByUrl(link)) {
                        continue;
                    }

                    String fullText = title + " " + description;

                    // ========== 필터링 로직 ==========

                    // 1. 회사명 정확히 포함 확인 (필수) - 단어 경계로 체크
                    String escapedName = org.getName().replaceAll("([\\(\\)\\[\\]\\{\\}])", "\\\\$1");
                    if (!fullText.matches(".*\\b" + escapedName + "\\b.*")) {
                        continue;
                    }

                    // 2. 종합뉴스 필터링 (여러 회사가 나열된 뉴스)
                    if (isSummaryNews(title, description)) {
                        log.trace("❌ 종합뉴스: {}", title);
                        continue;
                    }

                    // 3. 부정 키워드 필터링
                    if (containsNegativeKeyword(fullText)) {
                        log.trace("❌ 부정 키워드: {}", title);
                        continue;
                    }

                    // 4. 무관한 키워드 필터링
                    if (containsIrrelevantKeyword(fullText)) {
                        log.trace("❌ 무관한 내용: {}", title);
                        continue;
                    }

                    // 5. 긍정 키워드 확인
                    if (!containsPositiveKeyword(fullText)) {
                        continue;
                    }

                    // 6. 뉴스 품질 검증
                    if (!isQualityNews(title, description)) {
                        continue;
                    }

                    // ========== 저장 ==========

                    PositiveNews news = new PositiveNews();
                    news.setOrganization(org);
                    news.setOrganizationName(org.getName());
                    news.setTitle(title);
                    news.setDescription(description);
                    news.setUrl(link);
                    news.setPublishedDate(publishedDate);
                    news.setSource("NAVER");
                    news.setCategory(category);
                    news.setMatchedKeywords(keyword);

                    positiveNewsRepository.save(news);
                    positiveNewsRepository.flush();  // 즉시 DB 반영
                    processedUrls.add(link);
                    savedCount++;

                    log.trace("✅ 저장: {}", title);

                } catch (Exception e) {
                    log.trace("⚠️ 뉴스 항목 처리 실패: {}", e.getMessage());
                }
            }

            return savedCount;

        } catch (Exception e) {
            log.debug("❌ API 호출 실패 [{}]: {}", query, e.getMessage());
            return 0;
        }
    }

    // ========== 필터링 메서드들 ==========

    private boolean isSummaryNews(String title, String description) {
        String fullText = title + " " + description;

        // 방법 1: 명백한 종합뉴스 키워드
        if (SUMMARY_NEWS_KEYWORDS.stream().anyMatch(fullText::contains)) {
            return true;
        }

        // 방법 2: 여러 회사명이 나열되었는지 체크
        // "(주)" 또는 "㈜" 가 3개 이상이면 종합뉴스로 판단
        long companyMarkerCount = fullText.chars().filter(ch -> ch == '㈜').count() +
                (fullText.split("\\(주\\)").length - 1);

        if (companyMarkerCount >= 3) {
            return true;
        }

        // 방법 3: 타이틀에 "등", "外"가 있고 본문에 회사명이 여러 개
        if ((title.contains("등") || title.contains("外")) && companyMarkerCount >= 2) {
            return true;
        }

        return false;
    }

    private boolean containsNegativeKeyword(String text) {
        String lowerText = text.toLowerCase();
        return NEGATIVE_KEYWORDS.stream()
                .anyMatch(keyword -> lowerText.contains(keyword.toLowerCase()));
    }

    private boolean containsIrrelevantKeyword(String text) {
        String lowerText = text.toLowerCase();
        return IRRELEVANT_KEYWORDS.stream()
                .anyMatch(keyword -> lowerText.contains(keyword.toLowerCase()));
    }

    private boolean containsPositiveKeyword(String text) {
        return POSITIVE_KEYWORD_CATEGORIES.values().stream()
                .flatMap(List::stream)
                .anyMatch(text::contains);
    }

    private boolean isQualityNews(String title, String description) {
        if (title.length() < 10) return false;
        if (description == null || description.trim().length() < 20) return false;

        long specialCharCount = title.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                .count();
        if (specialCharCount > title.length() * 0.3) return false;

        if (title.chars().filter(ch -> ch == '?').count() > 2) return false;

        return true;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "")
                .replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LocalDate parseNaverDate(String dateStr) {
        try {
            // 네이버 날짜 형식: "Mon, 16 Dec 2024 10:30:00 +0900"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            log.trace("날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }

    /**
     * 수집 진행 상황 조회
     */
    public Map<String, Object> getCollectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isCollecting", isCollecting);
        status.put("totalOrganizations", totalOrgs.get());
        status.put("processedOrganizations", processedOrgs.get());
        status.put("collectedNews", totalCollectedNews.get());

        int progress = totalOrgs.get() > 0
                ? (int) (processedOrgs.get() * 100.0 / totalOrgs.get())
                : 0;
        status.put("progress", progress + "%");

        // 체크포인트 정보
        Long checkpoint = loadCheckpoint();
        if (checkpoint != null) {
            status.put("checkpoint", checkpoint);
            status.put("checkpointExists", true);
        }

        return status;
    }

    /**
     * 특정 조직의 뉴스 통계
     */
    public Map<String, Object> getNewsStatistics(Long orgId) {
        Map<String, Object> stats = new HashMap<>();

        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) {
            stats.put("error", "조직을 찾을 수 없습니다");
            return stats;
        }

        List<PositiveNews> newsList = positiveNewsRepository.findByOrganization(org);

        stats.put("organizationName", org.getName());
        stats.put("totalNews", newsList.size());

        // 카테고리별 통계
        Map<String, Long> categoryStats = newsList.stream()
                .collect(Collectors.groupingBy(
                        PositiveNews::getCategory,
                        Collectors.counting()
                ));
        stats.put("byCategory", categoryStats);

        // 연도별 통계
        Map<Integer, Long> yearStats = newsList.stream()
                .filter(news -> news.getPublishedDate() != null)
                .collect(Collectors.groupingBy(
                        news -> news.getPublishedDate().getYear(),
                        Collectors.counting()
                ));
        stats.put("byYear", yearStats);

        return stats;
    }
}
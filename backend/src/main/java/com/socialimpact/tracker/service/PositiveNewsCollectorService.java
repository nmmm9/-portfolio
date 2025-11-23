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
import org.springframework.context.ApplicationContext;
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
    private final ApplicationContext applicationContext;

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.search-url}")
    private String searchUrl;

    @Value("${positive-news.display:100}")
    private int display;

    private final AtomicInteger totalOrgs = new AtomicInteger(0);
    private final AtomicInteger processedOrgs = new AtomicInteger(0);
    private final AtomicInteger totalCollectedNews = new AtomicInteger(0);
    private volatile boolean isCollecting = false;
    private volatile boolean apiLimitReached = false;  // ← 추가

    private static final String CHECKPOINT_FILE = "news_collection_checkpoint.txt";

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

    private static final Set<String> NEGATIVE_KEYWORDS = new HashSet<>(Arrays.asList(
            "기소", "구속", "벌금", "과징금", "제재", "처벌", "징역", "실형", "법원", "재판", "소송",
            "고소", "고발", "수사", "검찰", "경찰", "횡령", "배임", "사기", "뇌물", "비리", "탈세",
            "적자", "손실", "부채", "파산", "회생", "구조조정", "감원", "해고", "정리해고", "희망퇴직",
            "사고", "화재", "폭발", "리콜", "결함", "불량", "오염", "파업", "태업", "쟁의",
            "논란", "비판", "질타", "반발", "항의", "의혹", "추정", "의심", "불투명",
            "청소원", "경비원", "사외이사", "이사회참석", "불참", "체력시험"
    ));

    private static final Set<String> IRRELEVANT_KEYWORDS = new HashSet<>(Arrays.asList(
            "날씨", "교통", "부동산", "아파트", "축구", "야구", "드라마", "영화", "연예인", "맛집"
    ));

    private static final Set<String> SUMMARY_NEWS_KEYWORDS = new HashSet<>(Arrays.asList(
            "장 마감 후", "장마감후", "e공시", "공시 눈에 띄네", "주요공시", "주요 공시",
            "증권사 주요 공시", "오늘의 공시", "공시 요약"
    ));

    private void saveCheckpoint(Long orgId) {
        try {
            FileWriter writer = new FileWriter(CHECKPOINT_FILE);
            writer.write(String.valueOf(orgId));
            writer.close();
            log.info("💾 체크포인트 저장: {}", orgId);
        } catch (Exception e) {
            log.warn("체크포인트 저장 실패: {}", e.getMessage());
        }
    }

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

    private void deleteCheckpoint() {
        try {
            new File(CHECKPOINT_FILE).delete();
            log.info("✅ 체크포인트 삭제");
        } catch (Exception e) {
            log.warn("체크포인트 삭제 실패: {}", e.getMessage());
        }
    }

    private void shutdownServer(String reason) {
        log.error("🛑 서버 종료 시작: {}", reason);
        log.info("📊 최종 통계:");
        log.info("   - 총 처리: {} / {}", processedOrgs.get(), totalOrgs.get());
        log.info("   - 수집 뉴스: {} 건", totalCollectedNews.get());

        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("👋 서버 종료 중...");
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void collectAllPositiveNews(int fromYear, int toYear) {
        collectAllPositiveNews(fromYear, toYear, false);
    }

    public void collectAllPositiveNews(int fromYear, int toYear, boolean clearBeforeCollect) {
        if (isCollecting) {
            log.warn("⚠️ 이미 수집 작업이 진행 중입니다!");
            return;
        }

        isCollecting = true;
        apiLimitReached = false;  // ← 초기화
        long startTime = System.currentTimeMillis();

        try {
            log.info("🚀 긍정 뉴스 수집 시작 ({} - {})", fromYear, toYear);

            if (clearBeforeCollect) {
                clearAllNews();
                deleteCheckpoint();
            }

            Long startFromId = loadCheckpoint();
            if (startFromId != null) {
                log.info("📍 체크포인트 발견: ID {} 부터 재시작", startFromId);
            }

            List<Organization> allOrgs = organizationRepository.findAll();

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

            for (Organization org : organizations) {
                // ← API 제한 체크 추가
                if (apiLimitReached) {
                    log.error("🚫 API 제한 감지, 전체 수집 중단");
                    saveCheckpoint(org.getId());
                    break;
                }

                try {
                    int newsCount = collectPositiveNewsForOrganization(org, fromYear, toYear);

                    // ← API 제한 체크
                    if (apiLimitReached) {
                        log.error("🚫 API 제한 감지, 수집 중단");
                        saveCheckpoint(org.getId());
                        break;
                    }

                    totalCollectedNews.addAndGet(newsCount);
                    processedOrgs.incrementAndGet();

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

                    saveCheckpoint(org.getId() + 1);
                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("작업 중단됨");
                    break;
                } catch (Exception e) {
                    log.error("❌ 회사 수집 실패 [{}]: {}", org.getName(), e.getMessage());
                }
            }

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

    @Transactional
    public int collectPositiveNewsForOrganization(Organization org, int fromYear, int toYear) {
        Set<String> processedUrls = ConcurrentHashMap.newKeySet();
        int totalCount = 0;

        for (Map.Entry<String, List<String>> entry : POSITIVE_KEYWORD_CATEGORIES.entrySet()) {
            // ← API 제한 체크 추가
            if (apiLimitReached) {
                log.warn("API 제한 플래그 감지, 키워드 루프 중단");
                return totalCount;
            }

            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                // ← API 제한 체크 추가
                if (apiLimitReached) {
                    log.warn("API 제한 플래그 감지, 검색 중단");
                    return totalCount;
                }

                try {
                    String query = org.getName() + " " + keyword;
                    int count = searchAndSaveNews(org, query, category, keyword, fromYear, toYear, processedUrls);
                    totalCount += count;

                    if (count > 0) {
                        log.info("  ✓ [{}] {}: {} 건", org.getName(), keyword, count);
                    }

                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("⚠️ 검색 실패 [{} + {}]: {}", org.getName(), keyword, e.getMessage());
                }
            }
        }

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

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("sort", "date")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(error -> {
                        String errorMsg = error.getMessage();
                        log.warn("API 호출 실패: {}", errorMsg);

                        if (errorMsg != null && errorMsg.contains("429")) {
                            log.error("🚫 API 할당량 초과! 서버 종료 시작");
                            apiLimitReached = true;  // ← 플래그 설정
                            shutdownServer("API 할당량 초과");  // ← 즉시 호출
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

                    LocalDate publishedDate = parseNaverDate(pubDate);
                    if (publishedDate == null ||
                            publishedDate.getYear() < fromYear ||
                            publishedDate.getYear() > toYear) {
                        continue;
                    }

                    if (processedUrls.contains(link) || positiveNewsRepository.existsByUrl(link)) {
                        continue;
                    }

                    String fullText = title + " " + description;

                    String escapedName = org.getName().replaceAll("([\\(\\)\\[\\]\\{\\}])", "\\\\$1");
                    if (!fullText.matches(".*\\b" + escapedName + "\\b.*")) {
                        continue;
                    }

                    if (isSummaryNews(title, description)) {
                        log.trace("❌ 종합뉴스: {}", title);
                        continue;
                    }

                    if (containsNegativeKeyword(fullText)) {
                        log.trace("❌ 부정 키워드: {}", title);
                        continue;
                    }

                    if (containsIrrelevantKeyword(fullText)) {
                        log.trace("❌ 무관한 내용: {}", title);
                        continue;
                    }

                    if (!containsPositiveKeyword(fullText)) {
                        continue;
                    }

                    if (!isQualityNews(title, description)) {
                        continue;
                    }

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
                    positiveNewsRepository.flush();
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

    private boolean isSummaryNews(String title, String description) {
        String fullText = title + " " + description;

        if (SUMMARY_NEWS_KEYWORDS.stream().anyMatch(fullText::contains)) {
            return true;
        }

        long companyMarkerCount = fullText.chars().filter(ch -> ch == '㈜').count() +
                (fullText.split("\\(주\\)").length - 1);

        if (companyMarkerCount >= 3) {
            return true;
        }

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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            log.trace("날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }

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

        Long checkpoint = loadCheckpoint();
        if (checkpoint != null) {
            status.put("checkpoint", checkpoint);
            status.put("checkpointExists", true);
        }

        return status;
    }

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

        Map<String, Long> categoryStats = newsList.stream()
                .collect(Collectors.groupingBy(
                        PositiveNews::getCategory,
                        Collectors.counting()
                ));
        stats.put("byCategory", categoryStats);

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
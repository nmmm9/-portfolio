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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // 키워드 카테고리 매핑
    private static final Map<String, List<String>> KEYWORD_CATEGORIES = Map.of(
            "기부", Arrays.asList("기부", "후원", "기증", "장학금", "지원금"),
            "봉사", Arrays.asList("봉사", "재능기부", "사회공헌"),
            "환경", Arrays.asList("친환경", "탄소중립", "재생에너지", "ESG"),
            "교육", Arrays.asList("교육지원", "멘토링", "장학생", "인재양성"),
            "일자리", Arrays.asList("일자리", "채용", "인턴십", "청년고용"),
            "지역사회", Arrays.asList("지역사회", "상생", "협력", "협약")
    );

    /**
     * 전체 조직의 긍정 뉴스 수집
     */
    public void collectAllPositiveNews(int fromYear, int toYear) {
        log.info("🚀 Starting positive news collection ({} - {})", fromYear, toYear);

        List<Organization> organizations = organizationRepository.findAll();
        log.info("📊 Found {} organizations to process", organizations.size());

        int successCount = 0;
        int skipCount = 0;

        for (Organization org : organizations) {
            try {
                int newsCount = collectNewsForOrganization(org, fromYear, toYear);
                if (newsCount > 0) {
                    successCount++;
                    log.info("✅ {}: {} news collected", org.getName(), newsCount);
                } else {
                    skipCount++;
                }

                // API 호출 제한 방지 (0.5초 대기)
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("❌ Error processing {}: {}", org.getName(), e.getMessage());
            }
        }

        log.info("✅ Collection completed! Success: {}, Skipped: {}", successCount, skipCount);
    }

    /**
     * 특정 조직의 긍정 뉴스 수집
     */
    @Transactional
    public int collectNewsForOrganization(Organization org, int fromYear, int toYear) {
        int totalCount = 0;

        // 각 카테고리별로 키워드 검색
        for (Map.Entry<String, List<String>> entry : KEYWORD_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                try {
                    String query = org.getName() + " " + keyword;
                    int count = searchAndSaveNews(org, query, category, keyword, fromYear, toYear);
                    totalCount += count;

                    // API 호출 제한 (0.3초)
                    Thread.sleep(300);

                } catch (Exception e) {
                    log.debug("⚠️ Search failed for {} + {}: {}", org.getName(), keyword, e.getMessage());
                }
            }
        }

        return totalCount;
    }

    /**
     * 네이버 뉴스 검색 및 저장
     */
    private int searchAndSaveNews(Organization org, String query, String category,
                                  String keyword, int fromYear, int toYear) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(searchUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("sort", "date") // 최신순
                            .build())
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
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

                    // 날짜 파싱
                    LocalDate publishedDate = parseNaverDate(pubDate);

                    // 연도 필터링
                    if (publishedDate == null ||
                            publishedDate.getYear() < fromYear ||
                            publishedDate.getYear() > toYear) {
                        continue;
                    }

                    // 중복 체크 (URL 기준)
                    if (positiveNewsRepository.existsByUrl(link)) {
                        continue;
                    }

                    // 긍정 키워드 확인
                    if (!containsPositiveKeyword(title + " " + description)) {
                        continue;
                    }

                    // PositiveNews 엔티티 생성
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
                    savedCount++;

                } catch (Exception e) {
                    log.debug("⚠️ Failed to save news item: {}", e.getMessage());
                }
            }

            return savedCount;

        } catch (Exception e) {
            log.error("❌ Search API failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * HTML 태그 제거
     */
    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "")
                .replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .trim();
    }

    /**
     * 네이버 날짜 형식 파싱 (예: "Mon, 23 Oct 2023 10:30:00 +0900")
     */
    private LocalDate parseNaverDate(String dateStr) {
        try {
            // "Mon, 23 Oct 2023 10:30:00 +0900" 형식
            String[] parts = dateStr.split(" ");
            if (parts.length >= 4) {
                int day = Integer.parseInt(parts[1]);
                String monthStr = parts[2];
                int year = Integer.parseInt(parts[3]);

                Map<String, Integer> months = Map.ofEntries(
                        Map.entry("Jan", 1), Map.entry("Feb", 2), Map.entry("Mar", 3),
                        Map.entry("Apr", 4), Map.entry("May", 5), Map.entry("Jun", 6),
                        Map.entry("Jul", 7), Map.entry("Aug", 8), Map.entry("Sep", 9),
                        Map.entry("Oct", 10), Map.entry("Nov", 11), Map.entry("Dec", 12)
                );

                int month = months.getOrDefault(monthStr, 1);
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            log.debug("⚠️ Date parsing failed: {}", dateStr);
        }
        return null;
    }

    /**
     * 긍정 키워드 포함 여부 확인
     */
    private boolean containsPositiveKeyword(String text) {
        for (List<String> keywords : KEYWORD_CATEGORIES.values()) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }
}
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
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

    private static final Map<String, List<String>> KEYWORD_CATEGORIES = Map.of(
            "기부", Arrays.asList("기부", "후원", "기증", "장학금", "지원금"),
            "봉사", Arrays.asList("봉사", "재능기부", "사회공헌"),
            "환경", Arrays.asList("친환경", "탄소중립", "재생에너지", "ESG"),
            "교육", Arrays.asList("교육지원", "멘토링", "장학생", "인재양성"),
            "일자리", Arrays.asList("일자리", "채용", "인턴십", "청년고용"),
            "지역사회", Arrays.asList("지역사회", "상생", "협력", "협약")
    );

    private static final List<String> EXCLUDE_KEYWORDS = Arrays.asList(
            "사망", "사고", "폭발", "화재", "소송", "분쟁", "논란",
            "비리", "횡령", "구속", "기소", "의혹", "스캔들"
    );

    private static final String KOREAN_PARTICLES = "가는이을를에의와과도만한테께서";

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

                Thread.sleep(500);

            } catch (Exception e) {
                log.error("❌ Error processing {}: {}", org.getName(), e.getMessage());
            }
        }

        log.info("✅ Collection completed! Success: {}, Skipped: {}", successCount, skipCount);
    }

    @Transactional
    public int collectNewsForOrganization(Organization org, int fromYear, int toYear) {
        int totalCount = 0;

        for (Map.Entry<String, List<String>> entry : KEYWORD_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                try {
                    String query = org.getName() + " " + keyword;

                    for (int page = 1; page <= 3; page++) {
                        int start = (page - 1) * 100 + 1;
                        int count = searchAndSaveNews(org, query, category, keyword, fromYear, toYear, start);
                        totalCount += count;

                        if (count == 0) break;

                        Thread.sleep(300);
                    }

                } catch (Exception e) {
                    log.debug("⚠️ Search failed for {} + {}: {}", org.getName(), keyword, e.getMessage());
                }
            }
        }

        return totalCount;
    }

    private int searchAndSaveNews(Organization org, String query, String category,
                                  String keyword, int fromYear, int toYear, int start) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(searchUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("start", start)
                            .queryParam("sort", "date")
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

                    LocalDate publishedDate = parseNaverDate(pubDate);

                    if (publishedDate == null ||
                            publishedDate.getYear() < fromYear ||
                            publishedDate.getYear() > toYear) {
                        continue;
                    }

                    if (positiveNewsRepository.existsByUrl(link)) {
                        continue;
                    }

                    // ⭐⭐⭐ 핵심: 원본 회사명만 매칭 (클린 네임 제외)
                    if (!checkExactMatch(title + " " + description, org.getName())) {
                        log.debug("⚠️ Filtered: {} - {}", org.getName(), title);
                        continue;
                    }

                    if (!containsPositiveKeyword(title + " " + description)) {
                        continue;
                    }

                    if (containsNegativeKeyword(title + " " + description)) {
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
                    savedCount++;

                    log.info("✅ SAVED: {} | {}", org.getName(), title);

                } catch (Exception e) {
                    log.debug("⚠️ Failed to save: {}", e.getMessage());
                }
            }

            return savedCount;

        } catch (Exception e) {
            log.error("❌ API failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * ⭐⭐⭐ 원본 회사명만 정확히 매칭
     * "(주)데코" 검색 시 "데코"만 있으면 제외
     */
    private boolean checkExactMatch(String text, String companyName) {
        if (!text.contains(companyName)) {
            return false;
        }

        String escaped = Pattern.quote(companyName);
        String pattern = "(^|[\\s\\(\\[\"'])(" + escaped + ")([\\s\\)\\]\"',\\.;" + KOREAN_PARTICLES + "]|$)";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);

        if (!m.find()) {
            return false;
        }

        int matchStart = m.start(2);
        int matchEnd = m.end(2);

        // 앞에 한글 체크
        if (matchStart > 0) {
            char prevChar = text.charAt(matchStart - 1);
            if (Character.isLetterOrDigit(prevChar) && isKorean(prevChar)) {
                return false;
            }
        }

        // 뒤에 한글 체크
        if (matchEnd < text.length()) {
            char nextChar = text.charAt(matchEnd);
            if (KOREAN_PARTICLES.indexOf(nextChar) >= 0) {
                return true;
            }
            if (Character.isLetterOrDigit(nextChar) && isKorean(nextChar)) {
                return false;
            }
        }

        return true;
    }

    private boolean isKorean(char ch) {
        return (ch >= 0xAC00 && ch <= 0xD7A3) ||
                (ch >= 0x1100 && ch <= 0x11FF) ||
                (ch >= 0x3130 && ch <= 0x318F);
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "")
                .replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&nbsp;", " ")
                .trim();
    }

    private LocalDate parseNaverDate(String dateStr) {
        try {
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

    private boolean containsNegativeKeyword(String text) {
        for (String keyword : EXCLUDE_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
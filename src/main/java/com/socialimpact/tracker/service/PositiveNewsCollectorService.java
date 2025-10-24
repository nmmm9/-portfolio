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

    // ==================== 긍정 키워드 카테고리 ====================
    private static final Map<String, List<String>> POSITIVE_KEYWORD_CATEGORIES = Map.ofEntries(
            Map.entry("기부", Arrays.asList("기부", "후원", "기증", "장학금", "지원금", "성금", "모금", "전달식")),
            Map.entry("봉사", Arrays.asList("봉사", "재능기부", "사회공헌", "자원봉사", "나눔")),
            Map.entry("환경", Arrays.asList("친환경", "탄소중립", "재생에너지", "ESG", "녹색경영", "환경보호", "탄소배출", "재활용")),
            Map.entry("교육", Arrays.asList("교육지원", "멘토링", "장학생", "인재양성", "교육기부", "직업훈련", "취업지원")),
            Map.entry("일자리", Arrays.asList("일자리창출", "채용확대", "신규채용", "청년고용", "정규직전환", "고용창출")),
            Map.entry("지역사회", Arrays.asList("지역사회", "상생협력", "MOU", "업무협약", "협약식", "파트너십")),
            Map.entry("윤리경영", Arrays.asList("윤리경영", "투명경영", "준법경영", "공정거래", "컴플라이언스")),
            Map.entry("혁신", Arrays.asList("R&D투자", "기술개발", "혁신", "특허", "연구개발"))
    );

    // ==================== 부정 키워드 (필터링용) ====================
    private static final Set<String> NEGATIVE_KEYWORDS = new HashSet<>(Arrays.asList(
            // 법적 문제
            "기소", "구속", "벌금", "과징금", "제재", "처벌", "징역", "실형", "집행유예",
            "법원", "재판", "소송", "고소", "고발", "수사", "압수수색", "검찰", "경찰",
            "횡령", "배임", "사기", "배임혐의", "배임수재", "뇌물", "비리", "부정", "탈세",

            // 경영 문제
            "적자", "손실", "부채", "파산", "회생", "부도", "워크아웃", "구조조정",
            "감원", "해고", "정리해고", "희망퇴직", "명예퇴직", "인력감축",
            "영업정지", "허가취소", "면허취소", "영업취소",

            // 사고/재해
            "사고", "화재", "폭발", "누출", "붕괴", "사망", "부상", "인명피해",
            "리콜", "결함", "하자", "불량", "오염",

            // 노사 갈등
            "파업", "태업", "쟁의", "노사분규", "갈등", "충돌", "시위", "농성",

            // 논란/비판
            "논란", "비판", "질타", "반발", "항의", "규탄", "성명", "우려",
            "의혹", "추정", "의심", "석연치", "불투명", "불분명",

            // 무관한 내용
            "청소원", "경비원", "미화원", "용역", "하청",
            "사외이사", "감사", "이사회참석", "불참",
            "체력시험", "신체검사", "달리기",

            // 추가 부정 키워드
            "고객정보유출", "개인정보유출", "해킹", "랜섬웨어",
            "담합", "카르텔", "독과점", "불공정",
            "환경오염", "무단배출", "불법폐기"
    ));

    // ==================== 무관한 키워드 (회사와 관련 없는 일반 뉴스) ====================
    private static final Set<String> IRRELEVANT_KEYWORDS = new HashSet<>(Arrays.asList(
            "날씨", "교통", "부동산", "아파트", "오피스텔",
            "축구", "야구", "농구", "골프", "스포츠",
            "드라마", "영화", "예능", "연예인", "가수",
            "맛집", "요리", "레시피", "음식점"
    ));

    /**
     * 전체 조직의 긍정 뉴스 수집
     */
    public void collectAllPositiveNews(int fromYear, int toYear) {
        log.info("🚀 긍정 뉴스 수집 시작 ({} - {})", fromYear, toYear);

        List<Organization> organizations = organizationRepository.findAll();
        log.info("📊 총 {} 개 조직 처리 예정", organizations.size());

        int successCount = 0;
        int totalNewsCount = 0;

        for (Organization org : organizations) {
            try {
                int newsCount = collectNewsForOrganization(org, fromYear, toYear);

                if (newsCount > 0) {
                    successCount++;
                    totalNewsCount += newsCount;
                    log.info("✅ [{}] {} 건의 뉴스 수집 완료", org.getName(), newsCount);
                } else {
                    log.info("⚠️ [{}] 수집된 뉴스 없음", org.getName());
                }

                // API 호출 제한 방지 (0.5초 대기)
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("❌ [{}] 처리 중 오류: {}", org.getName(), e.getMessage());
            }
        }

        log.info("✅ 수집 완료! 성공: {}개 조직, 총 {}건의 뉴스", successCount, totalNewsCount);
    }

    /**
     * 특정 조직의 긍정 뉴스 수집
     */
    @Transactional
    public int collectNewsForOrganization(Organization org, int fromYear, int toYear) {
        int totalCount = 0;
        Set<String> processedUrls = new HashSet<>();

        // 전략 1: 회사명 + 긍정 키워드 조합 검색
        for (Map.Entry<String, List<String>> entry : POSITIVE_KEYWORD_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();

            for (String keyword : keywords) {
                try {
                    String query = org.getName() + " " + keyword;
                    int count = searchAndSaveNews(org, query, category, keyword, fromYear, toYear, processedUrls);
                    totalCount += count;

                    Thread.sleep(300); // API 호출 제한

                } catch (Exception e) {
                    log.debug("⚠️ 검색 실패 [{} + {}]: {}", org.getName(), keyword, e.getMessage());
                }
            }
        }

        // 전략 2: 회사명만으로 검색 (추가 뉴스 발굴)
        try {
            int count = searchAndSaveNews(org, org.getName(), "전체", "전체", fromYear, toYear, processedUrls);
            totalCount += count;
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
            WebClient webClient = webClientBuilder.baseUrl(searchUrl).build();
            ObjectMapper mapper = new ObjectMapper();

            // 네이버 검색 API 호출
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("display", display)
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

                    // ========== 핵심 필터링 로직 ==========

                    // 1. 회사명 포함 확인 (필수)
                    if (!fullText.contains(org.getName())) {
                        continue;
                    }

                    // 2. 부정 키워드 필터링 (가장 중요!)
                    if (containsNegativeKeyword(fullText)) {
                        log.debug("❌ 부정 키워드 발견: {}", title);
                        continue;
                    }

                    // 3. 무관한 키워드 필터링
                    if (containsIrrelevantKeyword(fullText)) {
                        log.debug("❌ 무관한 내용: {}", title);
                        continue;
                    }

                    // 4. 긍정 키워드 확인 (최소 1개 이상 포함)
                    if (!containsPositiveKeyword(fullText)) {
                        continue;
                    }

                    // 5. 뉴스 품질 검증
                    if (!isQualityNews(title, description)) {
                        continue;
                    }

                    // ========== 검증 통과 -> 저장 ==========

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
                    processedUrls.add(link);
                    savedCount++;

                    log.debug("✅ 저장: [{}] {}", org.getName(), title);

                } catch (Exception e) {
                    log.debug("⚠️ 뉴스 항목 처리 실패: {}", e.getMessage());
                }
            }

            return savedCount;

        } catch (Exception e) {
            log.error("❌ 검색 API 호출 실패: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 부정 키워드 포함 여부 확인 (필터링용)
     */
    private boolean containsNegativeKeyword(String text) {
        String lowerText = text.toLowerCase();
        for (String negativeKeyword : NEGATIVE_KEYWORDS) {
            if (lowerText.contains(negativeKeyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 무관한 키워드 포함 여부 확인
     */
    private boolean containsIrrelevantKeyword(String text) {
        String lowerText = text.toLowerCase();
        for (String irrelevantKeyword : IRRELEVANT_KEYWORDS) {
            if (lowerText.contains(irrelevantKeyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 긍정 키워드 포함 여부 확인
     */
    private boolean containsPositiveKeyword(String text) {
        for (List<String> keywords : POSITIVE_KEYWORD_CATEGORIES.values()) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 뉴스 품질 검증
     */
    private boolean isQualityNews(String title, String description) {
        // 1. 제목이 너무 짧으면 제외 (광고성)
        if (title.length() < 10) {
            return false;
        }

        // 2. 제목에 특수문자가 너무 많으면 제외
        long specialCharCount = title.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
                .count();
        if (specialCharCount > title.length() * 0.3) {
            return false;
        }

        // 3. 설명이 비어있으면 제외
        if (description == null || description.trim().length() < 20) {
            return false;
        }

        // 4. 제목에 '?' 가 많으면 제외 (추측성 기사)
        if (title.chars().filter(ch -> ch == '?').count() > 2) {
            return false;
        }

        return true;
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
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 네이버 날짜 형식 파싱
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
            log.debug("⚠️ 날짜 파싱 실패: {}", dateStr);
        }
        return null;
    }

    /**
     * 특정 조직의 긍정 뉴스 통계
     */
    public Map<String, Object> getNewsStatistics(Long organizationId) {
        List<PositiveNews> allNews = positiveNewsRepository
                .findByOrganization_IdOrderByPublishedDateDesc(organizationId, org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allNews.size());
        stats.put("byCategory", allNews.stream()
                .collect(Collectors.groupingBy(PositiveNews::getCategory, Collectors.counting())));
        stats.put("byYear", allNews.stream()
                .collect(Collectors.groupingBy(n -> n.getPublishedDate().getYear(), Collectors.counting())));

        return stats;
    }
}
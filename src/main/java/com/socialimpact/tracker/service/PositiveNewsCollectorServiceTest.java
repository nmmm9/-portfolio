package com.socialimpact.tracker.service;

import com.socialimpact.tracker.entity.Organization;
import com.socialimpact.tracker.entity.PositiveNews;
import com.socialimpact.tracker.repository.OrganizationRepository;
import com.socialimpact.tracker.repository.PositiveNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("긍정 뉴스 수집 서비스 테스트")
class PositiveNewsCollectorServiceTest {

    @Mock
    private PositiveNewsRepository positiveNewsRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private PositiveNewsCollectorService service;

    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = new Organization();
        testOrg.setId(1L);
        testOrg.setName("삼성전자");
        testOrg.setType("상장사");

        // Mock 설정
        when(positiveNewsRepository.existsByUrl(any())).thenReturn(false);
    }

    @Test
    @DisplayName("부정 키워드 필터링 - 법적 문제")
    void testNegativeKeywordFiltering_Legal() throws Exception {
        // Given
        String[] negativeTexts = {
                "삼성전자 사외이사 기소",
                "삼성전자 횡령 혐의로 구속",
                "삼성전자 법원 판결",
                "삼성전자 검찰 수사",
                "삼성전자 과징금 부과"
        };

        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsNegativeKeyword", String.class);
        method.setAccessible(true);

        // When & Then
        for (String text : negativeTexts) {
            boolean result = (boolean) method.invoke(service, text);
            assertTrue(result, "부정 키워드를 감지해야 함: " + text);
        }
    }

    @Test
    @DisplayName("부정 키워드 필터링 - 경영 문제")
    void testNegativeKeywordFiltering_Management() throws Exception {
        // Given
        String[] negativeTexts = {
                "삼성전자 적자 전환",
                "삼성전자 구조조정 단행",
                "삼성전자 정리해고 발표",
                "삼성전자 희망퇴직 실시",
                "삼성전자 영업정지 처분"
        };

        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsNegativeKeyword", String.class);
        method.setAccessible(true);

        // When & Then
        for (String text : negativeTexts) {
            boolean result = (boolean) method.invoke(service, text);
            assertTrue(result, "부정 키워드를 감지해야 함: " + text);
        }
    }

    @Test
    @DisplayName("부정 키워드 필터링 - 무관한 내용")
    void testNegativeKeywordFiltering_Irrelevant() throws Exception {
        // Given
        String[] negativeTexts = {
                "삼성전자 청소원 채용 체력시험",
                "삼성전자 사외이사 이사회 불참",
                "삼성전자 경비원 신체검사"
        };

        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsNegativeKeyword", String.class);
        method.setAccessible(true);

        // When & Then
        for (String text : negativeTexts) {
            boolean result = (boolean) method.invoke(service, text);
            assertTrue(result, "무관한 키워드를 감지해야 함: " + text);
        }
    }

    @Test
    @DisplayName("긍정 키워드 확인")
    void testPositiveKeywordDetection() throws Exception {
        // Given
        String[] positiveTexts = {
                "삼성전자, 장학금 10억원 기부",
                "삼성전자 사회공헌 봉사활동",
                "삼성전자 ESG 경영 강화",
                "삼성전자 청년 일자리 창출",
                "삼성전자 지역사회 상생협력"
        };

        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsPositiveKeyword", String.class);
        method.setAccessible(true);

        // When & Then
        for (String text : positiveTexts) {
            boolean result = (boolean) method.invoke(service, text);
            assertTrue(result, "긍정 키워드를 감지해야 함: " + text);
        }
    }

    @Test
    @DisplayName("뉴스 품질 검증 - 제목 길이")
    void testQualityNews_TitleLength() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        method.setAccessible(true);

        // Given - 너무 짧은 제목
        String shortTitle = "삼성전자";
        String description = "삼성전자가 장학금을 전달했습니다.";

        // When
        boolean result = (boolean) method.invoke(service, shortTitle, description);

        // Then
        assertFalse(result, "제목이 너무 짧으면 거부해야 함");
    }

    @Test
    @DisplayName("뉴스 품질 검증 - 특수문자 비율")
    void testQualityNews_SpecialCharacters() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        method.setAccessible(true);

        // Given - 특수문자가 많은 제목
        String specialTitle = "!!!삼성전자!!! ***대박*** ???";
        String description = "삼성전자가 장학금을 전달했습니다.";

        // When
        boolean result = (boolean) method.invoke(service, specialTitle, description);

        // Then
        assertFalse(result, "특수문자가 많으면 거부해야 함");
    }

    @Test
    @DisplayName("뉴스 품질 검증 - 설명 길이")
    void testQualityNews_DescriptionLength() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        method.setAccessible(true);

        // Given - 설명이 너무 짧음
        String title = "삼성전자 장학금 전달";
        String shortDescription = "전달함";

        // When
        boolean result = (boolean) method.invoke(service, title, shortDescription);

        // Then
        assertFalse(result, "설명이 너무 짧으면 거부해야 함");
    }

    @Test
    @DisplayName("뉴스 품질 검증 - 추측성 기사")
    void testQualityNews_QuestionMarks() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        method.setAccessible(true);

        // Given - 물음표가 많은 제목
        String questionTitle = "삼성전자 기부? 정말? 진짜?";
        String description = "삼성전자가 장학금을 전달했다고 합니다.";

        // When
        boolean result = (boolean) method.invoke(service, questionTitle, description);

        // Then
        assertFalse(result, "물음표가 많으면 거부해야 함");
    }

    @Test
    @DisplayName("뉴스 품질 검증 - 정상적인 뉴스")
    void testQualityNews_ValidNews() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        method.setAccessible(true);

        // Given - 정상적인 뉴스
        String title = "삼성전자, 청년 일자리 창출을 위한 장학금 10억원 전달";
        String description = "삼성전자가 미래 인재 양성을 위해 청년들에게 장학금 10억원을 전달했다.";

        // When
        boolean result = (boolean) method.invoke(service, title, description);

        // Then
        assertTrue(result, "정상적인 뉴스는 통과해야 함");
    }

    @Test
    @DisplayName("HTML 태그 제거")
    void testCleanHtml() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("cleanHtml", String.class);
        method.setAccessible(true);

        // Given
        String htmlText = "<b>삼성전자</b>가 <strong>장학금</strong>을 전달했습니다.";

        // When
        String result = (String) method.invoke(service, htmlText);

        // Then
        assertEquals("삼성전자가 장학금을 전달했습니다.", result);
    }

    @Test
    @DisplayName("HTML 엔티티 변환")
    void testCleanHtml_Entities() throws Exception {
        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("cleanHtml", String.class);
        method.setAccessible(true);

        // Given
        String htmlText = "&quot;삼성전자&quot; &amp; &lt;현대차&gt;";

        // When
        String result = (String) method.invoke(service, htmlText);

        // Then
        assertEquals("\"삼성전자\" & <현대차>", result);
    }

    @Test
    @DisplayName("무관한 키워드 필터링")
    void testIrrelevantKeywordFiltering() throws Exception {
        // Given
        String[] irrelevantTexts = {
                "삼성전자 스마트폰 맛집 추천",
                "삼성전자 본사 근처 날씨",
                "삼성전자 직원 축구 경기",
                "삼성전자 CEO 드라마 출연"
        };

        Method method = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsIrrelevantKeyword", String.class);
        method.setAccessible(true);

        // When & Then
        for (String text : irrelevantTexts) {
            boolean result = (boolean) method.invoke(service, text);
            assertTrue(result, "무관한 키워드를 감지해야 함: " + text);
        }
    }

    @Test
    @DisplayName("통합 필터링 테스트 - 긍정적 뉴스")
    void testIntegratedFiltering_PositiveNews() throws Exception {
        // Given
        String title = "삼성전자, 저소득층 청소년 교육지원 장학금 5억원 전달";
        String description = "삼성전자가 미래 인재 양성을 위해 저소득층 청소년 100명에게 장학금 5억원을 전달했다.";
        String fullText = title + " " + description;

        // Private 메서드 접근
        Method negativeMethod = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsNegativeKeyword", String.class);
        negativeMethod.setAccessible(true);

        Method positiveMethod = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsPositiveKeyword", String.class);
        positiveMethod.setAccessible(true);

        Method qualityMethod = PositiveNewsCollectorService.class
                .getDeclaredMethod("isQualityNews", String.class, String.class);
        qualityMethod.setAccessible(true);

        // When
        boolean hasNegative = (boolean) negativeMethod.invoke(service, fullText);
        boolean hasPositive = (boolean) positiveMethod.invoke(service, fullText);
        boolean isQuality = (boolean) qualityMethod.invoke(service, title, description);

        // Then
        assertFalse(hasNegative, "부정 키워드가 없어야 함");
        assertTrue(hasPositive, "긍정 키워드가 있어야 함");
        assertTrue(isQuality, "품질이 좋아야 함");
    }

    @Test
    @DisplayName("통합 필터링 테스트 - 부정적 뉴스")
    void testIntegratedFiltering_NegativeNews() throws Exception {
        // Given
        String title = "삼성전자 사외이사, 횡령 혐의로 법원 기소";
        String description = "삼성전자의 사외이사가 횡령 혐의로 검찰에 기소되어 법원의 판결을 기다리고 있다.";
        String fullText = title + " " + description;

        // Private 메서드 접근
        Method negativeMethod = PositiveNewsCollectorService.class
                .getDeclaredMethod("containsNegativeKeyword", String.class);
        negativeMethod.setAccessible(true);

        // When
        boolean hasNegative = (boolean) negativeMethod.invoke(service, fullText);

        // Then
        assertTrue(hasNegative, "부정 키워드를 감지해야 함");
    }
}
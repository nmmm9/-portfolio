// src/main/java/com/impacttracker/backend/ingest/DonationParser.java
package com.impacttracker.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DART 문서(문자열)에서 기부/후원 관련 금액을 추출해 KRW로 환산.
 * - 키워드(기부/후원/사회공헌/출연금 등)가 "최소 1개 이상" 존재할 때만 값을 채택합니다(오탐 감소).
 * - 숫자 단위: 원/천/만원/억/조 등을 KRW로 환산.
 * - 결과는 BigDecimal(원) 단위로 반환.
 */
@Component
public class DonationParser {

    private static final Logger log = LoggerFactory.getLogger(DonationParser.class);

    /** 키워드 주변(앞뒤)에서만 숫자 스캔 (오탐 감소) */
    private static final int WINDOW_RADIUS = 600;

    /** 비정상 큰 값(오탐) 컷오프: 1,000조원(= 1e15 KRW) 초과면 폐기 */
    private static final BigDecimal HARD_MAX = new BigDecimal("1000000000000000");

    /** 단위 포함 숫자 패턴 (긴 단위 우선) */
    private static final Pattern NUM_UNIT = Pattern.compile(
            "([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.(\\d+))?\\s*(조원|조|억원|억|백만원|백만|십만원|십만|만원|만|천원|천|원)?"
    );

    /** 신호 키워드(있을 때만 스캔 허용) */
    private static final String[] SIGNAL_KEYWORDS = new String[]{
            "기부", "기부금", "기부 활동", "기부활동", "기부금품",
            "후원", "후원금", "협찬",
            "사회공헌", "사회 공헌", "사회공헌활동", "사회 공헌 활동",
            "출연금", "장학금", "성금", "기탁"
    };

    /**
     * 핵심 API: 텍스트/HTML/XML에서 금액 추출(KRW).
     * 키워드가 하나도 없으면 null 반환.
     */
    public BigDecimal extractDonationAmount(String xmlOrText) {
        if (xmlOrText == null || xmlOrText.isBlank()) return null;

        final String text = normalize(xmlOrText);

        boolean anyKeyword = false;
        BigDecimal best = null;

        for (String kw : SIGNAL_KEYWORDS) {
            int idx = text.indexOf(kw);
            if (idx < 0) continue;
            anyKeyword = true;
            int from = Math.max(0, idx - WINDOW_RADIUS);
            int to   = Math.min(text.length(), idx + WINDOW_RADIUS);
            BigDecimal cand = scanWindow(text.substring(from, to));
            best = max(best, cand);
        }

        // 키워드가 전혀 없으면 "기부/후원 맥락 없음"으로 간주
        if (!anyKeyword) return null;

        // 비정상 초대형 값 컷
        if (best != null && best.compareTo(HARD_MAX) > 0) {
            log.debug("[donation-parser] filtered out too-large value: {}", best.toPlainString());
            return null;
        }
        return best;
    }

    /** 연월이 추가로 들어오는 오버로드(현재는 동일 처리) */
    public BigDecimal extractDonationAmount(String xmlOrText, YearMonth ym) {
        return extractDonationAmount(xmlOrText);
    }

    /* ================= 내부 유틸 ================= */

    /** 태그 제거/공백 정리 */
    private String normalize(String s) {
        String t = s.replaceAll("(?s)<[^>]*>", " "); // 태그 제거
        t = t.replace('\u00A0', ' ');                // NBSP → SPACE
        t = t.replaceAll("[\\r\\n\\t]+", " ");       // 줄바꿈/탭 축약
        t = t.replaceAll(" +", " ");                 // 다중 공백 축약
        return t;
    }

    /** 주어진 창(window)에서 숫자+단위를 찾아 KRW로 환산한 후보들 중 최대값 반환 */
    private BigDecimal scanWindow(String window) {
        Matcher m = NUM_UNIT.matcher(window);
        List<BigDecimal> candidates = new ArrayList<>();
        while (m.find()) {
            String intPart = m.group(1);   // 예: 1,234
            String frac    = m.group(2);   // 예: 56
            String unit    = m.group(3);   // 예: 억원

            if (intPart == null) continue;
            String num = intPart.replace(",", "");
            if (frac != null && !frac.isBlank()) {
                num = num + "." + frac;
            }

            try {
                BigDecimal v = new BigDecimal(num);
                v = applyUnit(v, unit);
                if (v.signum() > 0) {
                    candidates.add(v);
                }
            } catch (NumberFormatException ignore) {
                // skip
            }
        }

        BigDecimal best = null;
        for (BigDecimal c : candidates) best = max(best, c);
        return best;
    }

    /** 단위 환산 → KRW */
    private BigDecimal applyUnit(BigDecimal v, String unit) {
        if (unit == null || unit.isBlank() || "원".equals(unit)) return v;

        switch (unit) {
            case "조원":
            case "조":
                return v.multiply(new BigDecimal("1000000000000")); // 1조 = 10^12
            case "억원":
            case "억":
                return v.multiply(new BigDecimal("100000000"));     // 1억 = 10^8
            case "백만원":
            case "백만":
                return v.multiply(new BigDecimal("1000000"));       // 100만 = 10^6
            case "십만원":
            case "십만":
                return v.multiply(new BigDecimal("100000"));        // 10만 = 10^5
            case "만원":
            case "만":
                return v.multiply(new BigDecimal("10000"));         // 1만 = 10^4
            case "천원":
            case "천":
                return v.multiply(new BigDecimal("1000"));          // 1천 = 10^3
            default:
                return v; // 미식별 단위는 원으로 가정
        }
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }
}

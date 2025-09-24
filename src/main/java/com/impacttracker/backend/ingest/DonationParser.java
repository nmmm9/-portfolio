package com.impacttracker.backend.ingest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DonationParser {

    // 예: "사회공헌비용 1,234,567,890원", "기부금 12,345백만원", "후원비 123만원"
    private static final Pattern WON_PATTERN =
            Pattern.compile("(사회공헌|기부|후원)[^\\d]*(\\d{1,3}(?:,\\d{3})+|\\d+)(\\s*(백만원|만원|원))?");

    public BigDecimal extractDonationAmount(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher m = WON_PATTERN.matcher(text.replace("\u00A0"," "));
        while (m.find()) {
            String amountStr = m.group(2).replace(",", "");
            String unit = m.group(4); // 백만원/만원/원
            try {
                BigDecimal val = new BigDecimal(amountStr);
                if (unit != null) {
                    if (unit.contains("백만원")) {
                        val = val.multiply(BigDecimal.valueOf(100_000_000L)); // 백만원 → 원
                    } else if (unit.contains("만원")) {
                        val = val.multiply(BigDecimal.valueOf(10_000L));
                    }
                }
                return val;
            } catch (NumberFormatException ignore) { /* 다음 매치 */ }
        }
        return null;
    }
}

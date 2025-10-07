package com.impacttracker.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 웹 크롤링 설정
 *
 * application.yml 예시:
 * impacttracker:
 *   crawler:
 *     enabled: true
 *     user-agent: "Mozilla/5.0..."
 *     timeout-ms: 10000
 *     delay-between-requests-ms: 2000
 *     max-retries: 3
 *     targets:
 *       - name: "DART"
 *         base-url: "https://dart.fss.or.kr"
 *         enabled: true
 *       - name: "ESG"
 *         base-url: "https://esg.krx.co.kr"
 *         enabled: false
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "impacttracker.crawler")
public class CrawlerConfig {

    private boolean enabled = true;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private int timeoutMs = 10000;
    private int delayBetweenRequestsMs = 2000;
    private int maxRetries = 3;
    private int maxConcurrentRequests = 5;

    private List<CrawlerTarget> targets = new ArrayList<>();

    private RateLimiting rateLimiting = new RateLimiting();
    private Proxy proxy = new Proxy();

    /**
     * 크롤링 대상 사이트 설정
     */
    @Getter
    @Setter
    public static class CrawlerTarget {
        private String name;
        private String baseUrl;
        private boolean enabled = true;
        private int timeoutMs;
        private String selector; // CSS selector for data extraction
    }

    /**
     * 요청 제한 설정
     */
    @Getter
    @Setter
    public static class RateLimiting {
        private boolean enabled = true;
        private int requestsPerMinute = 30;
        private int requestsPerHour = 1000;
    }

    /**
     * 프록시 설정 (선택사항)
     */
    @Getter
    @Setter
    public static class Proxy {
        private boolean enabled = false;
        private String host;
        private int port;
        private String username;
        private String password;
    }
}
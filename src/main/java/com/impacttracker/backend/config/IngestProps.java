package com.impacttracker.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * application.yml / application-*.yml 의 impacttracker.ingest.* 바인딩
 *
 * 예)
 * impacttracker:
 *   ingest:
 *     on-startup: true
 *     months-back: 0
 *     parallelism: 2
 */
@Configuration
@ConfigurationProperties(prefix = "impacttracker.ingest")
public class IngestProps {

    private boolean onStartup = true;
    private int monthsBack = 0;     // 테스트는 0(이번달만)
    private int parallelism = 2;    // 동시 수집 스레드 수(너무 크게 올리지 말기)

    // (전사 자동수집이면 corp 목록을 쓰지 않지만, 확장성을 위해 남겨둠)
    private List<Corp> corps;

    private PublicData publicData = new PublicData();

    // --- nested types ---
    public static class Corp {
        private String name;
        private String corpCode;
        private Long orgId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCorpCode() { return corpCode; }
        public void setCorpCode(String corpCode) { this.corpCode = corpCode; }

        public Long getOrgId() { return orgId; }
        public void setOrgId(Long orgId) { this.orgId = orgId; }
    }

    public static class PublicData {
        private boolean enabled = false;
        private String apiUrl;
        private String apiKey;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    // --- getters/setters ---
    public boolean isOnStartup() { return onStartup; }
    public void setOnStartup(boolean onStartup) { this.onStartup = onStartup; }

    public int getMonthsBack() { return monthsBack; }
    public void setMonthsBack(int monthsBack) { this.monthsBack = monthsBack; }

    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }

    public List<Corp> getCorps() { return corps; }
    public void setCorps(List<Corp> corps) { this.corps = corps; }

    public PublicData getPublicData() { return publicData; }
    public void setPublicData(PublicData publicData) { this.publicData = publicData; }
}

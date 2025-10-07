package com.impacttracker.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "impacttracker.ingest")
public class IngestProps {

    private boolean onStartup = true;
    private int yearsBack = 3;      // ← 이름 변경! (monthsBack → yearsBack)
    private int parallelism = 2;
    private int maxTargets = 50;

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

    public int getYearsBack() { return yearsBack; }
    public void setYearsBack(int yearsBack) { this.yearsBack = yearsBack; }

    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }

    public int getMaxTargets() { return maxTargets; }
    public void setMaxTargets(int maxTargets) { this.maxTargets = maxTargets; }

    public List<Corp> getCorps() { return corps; }
    public void setCorps(List<Corp> corps) { this.corps = corps; }

    public PublicData getPublicData() { return publicData; }
    public void setPublicData(PublicData publicData) { this.publicData = publicData; }
}
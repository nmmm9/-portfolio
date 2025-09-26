package com.impacttracker.backend.web;

import com.impacttracker.backend.domain.*;
import com.impacttracker.backend.service.KpiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    // 상단 숫자 카드: 최근 12개월 합
    @GetMapping("/kpi/cards")
    public Map<String, Object> cards(@RequestParam Long orgId,
                                     @RequestParam(defaultValue = "12") int months) {
        var sums = kpiService.rollingSum(orgId, months);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("co2e", sums.getOrDefault(KpiMetric.CO2E_REDUCED_TON, BigDecimal.ZERO));
        out.put("volunteerHours", sums.getOrDefault(KpiMetric.VOLUNTEER_HOURS, BigDecimal.ZERO));
        out.put("donationAmountKrw", sums.getOrDefault(KpiMetric.DONATION_AMOUNT_KRW, BigDecimal.ZERO));
        out.put("peopleServed", sums.getOrDefault(KpiMetric.PEOPLE_SERVED_COUNT, BigDecimal.ZERO));
        return out;
    }

    // 승인율
    @GetMapping("/kpi/approval-rate")
    public Map<String, Object> approvalRate(@RequestParam Long orgId,
                                            @RequestParam(defaultValue = "12") int months) {
        double rate = kpiService.approvalRate(orgId, months);
        return Map.of("approvalRatePct", rate);
    }

    // 시계열
    @GetMapping("/kpi/series")
    public List<KpiMonthly> series(@RequestParam Long orgId,
                                   @RequestParam KpiMetric metric) {
        return kpiService.series(orgId, metric);
    }

    // CSV 업로드: 헤더 = orgId,projectId,periodYm,metric,value,source,approved
    @PostMapping(path="/kpi/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws Exception {
        int ok=0, fail=0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line; boolean headerSkipped=false;
            while ((line = br.readLine()) != null) {
                if (!headerSkipped) { headerSkipped=true; continue; }
                if (line.isBlank()) continue;
                String[] t = splitCsv(line);
                try {
                    Long orgId = parseLong(t[0]);
                    Long projectId = t[1].isBlank()? null : Long.parseLong(t[1]);
                    String periodYm = t[2];
                    KpiMetric metric = KpiMetric.valueOf(t[3]);
                    BigDecimal value = new BigDecimal(t[4]);
                    String source = t.length>5 ? t[5] : "MANUAL";
                    boolean approved = t.length>6 ? Boolean.parseBoolean(t[6]) : true;
                    kpiService.upsertMonthly(orgId, projectId, periodYm, metric, value, source, approved);
                    ok++;
                } catch (Exception ex) { fail++; }
            }
        }
        return Map.of("insertedOrUpdated", ok, "failed", fail);
    }

    private Long parseLong(String s){ return (s==null||s.isBlank())? null: Long.parseLong(s); }

    private String[] splitCsv(String line){
        // 간단 CSV: 쿼트 없는 기준 (필요 시 commons-csv로 교체)
        return line.split("\\s*,\\s*");
    }
}

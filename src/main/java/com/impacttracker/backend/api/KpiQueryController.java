package com.impacttracker.backend.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/kpi")
public class KpiQueryController {
    private final JdbcTemplate jdbc;
    public KpiQueryController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam Long orgId, @RequestParam(defaultValue="12") int months) {
        String sumSql = """
            SELECT
              COALESCE(SUM(emissions_reduced_tco2e),0) co2,
              COALESCE(SUM(volunteer_hours),0) hrs,
              COALESCE(SUM(donation_amount_krw),0) krw,
              COALESCE(SUM(people_served),0) ppl
            FROM kpi_monthly
            WHERE organization_id=? 
              AND (year*100+month) >= (DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? MONTH),'%Y')*100+DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? MONTH),'%m'))
        """;
        Map<String,Object> r = jdbc.queryForMap(sumSql, orgId, months, months);
        Integer appr = jdbc.queryForObject("""
            SELECT ROUND(100*SUM(CASE WHEN status='APPROVED' THEN 1 ELSE 0 END)/NULLIF(COUNT(*),0),0)
            FROM verification_log WHERE organization_id=? AND created_at >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
        """, Integer.class, orgId);
        r.put("approvalRate", Optional.ofNullable(appr).orElse(0));
        return r;
    }

    @GetMapping("/by-project")
    public List<Map<String,Object>> byProject(@RequestParam Long orgId, @RequestParam(defaultValue="12") int months) {
        String sql = """
            SELECT COALESCE(p.name,'(Unassigned)') name,
                   COALESCE(SUM(k.emissions_reduced_tco2e),0) co2,
                   COALESCE(SUM(k.volunteer_hours),0) hrs
            FROM kpi_monthly k LEFT JOIN project p ON p.id=k.project_id
            WHERE k.organization_id=? 
              AND (k.year*100+k.month) >= (DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? MONTH),'%Y')*100+DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? MONTH),'%m'))
            GROUP BY p.name ORDER BY co2 DESC
        """;
        return jdbc.queryForList(sql, orgId, months, months);
    }
}

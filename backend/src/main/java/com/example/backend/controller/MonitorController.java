package com.example.backend.controller;

import com.example.backend.dto.MetricDTO;
import com.example.backend.service.HealthService;
import com.example.backend.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private HealthService healthService;

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData(@RequestParam(required = false) String ip,
                                                @RequestParam(required = false) String username) {
        Map<String, Object> dashboard = new HashMap<>();

        List<String> servers = monitorService.getMonitoredIpsByUsername(username);
        dashboard.put("servers", servers);

        String targetIp = ip;
        if ((targetIp == null || targetIp.isEmpty()) && !servers.isEmpty()) {
            targetIp = servers.get(0);
        }
        if (targetIp != null && !servers.contains(targetIp)) {
            targetIp = null;
        }
        dashboard.put("selectedIp", targetIp);

        if (targetIp != null) {
            List<MetricDTO> history = monitorService.getHistory(targetIp);
            dashboard.put("history", history);
            Map<String, Object> current = monitorService.getRealtime(targetIp);
            dashboard.put("current", current);
            dashboard.put("monitorEnabled", monitorService.isServerMonitorEnabledByUsername(username, targetIp));
            dashboard.put("healthState", healthService.buildHealthState(username, targetIp, current, history));
        } else {
            dashboard.put("history", List.of());
            dashboard.put("current", Map.of());
            dashboard.put("monitorEnabled", false);
            dashboard.put("healthState", Map.of());
        }

        return dashboard;
    }
}

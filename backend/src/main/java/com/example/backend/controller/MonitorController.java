package com.example.backend.controller;

import com.example.backend.dto.MetricDTO;
import com.example.backend.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData(@RequestParam(required = false) String ip) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // 获取所有已监控的IP列表
        List<String> servers = monitorService.getMonitoredIps();
        dashboard.put("servers", servers);
        
        // 如果没有指定IP，默认取第一个
        String targetIp = ip;
        if ((targetIp == null || targetIp.isEmpty()) && !servers.isEmpty()) {
            targetIp = servers.get(0);
        }
        
        dashboard.put("selectedIp", targetIp);
        
        if (targetIp != null) {
            // 获取过去 60 分钟的数据趋势
            List<MetricDTO> history = monitorService.getHistory(targetIp);
            dashboard.put("history", history);
            
            // 获取当前详细硬件信息
            Map<String, Object> current = monitorService.getRealtime(targetIp);
            dashboard.put("current", current);
        } else {
            dashboard.put("history", List.of());
            dashboard.put("current", Map.of());
        }
        
        return dashboard;
    }
}

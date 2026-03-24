package com.example.backend.tool;

import com.example.backend.agent.AgentExecutionContext;
import com.example.backend.service.MonitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetricsTool implements AgentTool {

    private final MonitorService monitorService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "get_server_metrics";
    }

    @Override
    public String getDescription() {
        return "获取指定服务器最近的系统监控数据，包含 CPU、内存、网卡和磁盘速率。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "serverIp", Map.of(
                        "type", "string",
                        "description", "目标服务器 IP，默认当前会话服务器"
                ),
                "timeRange", Map.of(
                        "type", "string",
                        "description", "时间范围，只能是 30m、1h、2h"
                )
        ));
        schema.put("required", java.util.List.of("serverIp", "timeRange"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
        String targetIp = String.valueOf(arguments.getOrDefault("serverIp", context.getServerIp())).trim();
        if (!StringUtils.hasText(targetIp)) {
            targetIp = context.getServerIp();
        }

        String timeRange = normalizeTimeRange(String.valueOf(arguments.getOrDefault("timeRange", "30m")));
        context.setMetricsRequested(true);

        try {
            Map<String, Object> metrics = new LinkedHashMap<>(monitorService.getMetrics(targetIp, timeRange));
            int historyPoints = toInt(metrics.get("historyPoints"));
            if (historyPoints <= 0) {
                Map<String, Object> sampleResult = monitorService.sampleMetricsOnce(
                        targetIp,
                        context.getUsername(),
                        context.getPassword()
                );
                metrics = new LinkedHashMap<>(monitorService.getMetrics(targetIp, timeRange));
                metrics.put("sampleOnceWhenEmpty", sampleResult);
            }
            return objectMapper.writeValueAsString(metrics);
        } catch (Exception e) {
            return "ERROR: 获取监控数据失败: " + e.getMessage();
        }
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String normalizeTimeRange(String timeRange) {
        String normalized = String.valueOf(timeRange).trim().toLowerCase();
        if ("30m".equals(normalized) || "1h".equals(normalized) || "2h".equals(normalized)) {
            return normalized;
        }
        return "30m";
    }
}

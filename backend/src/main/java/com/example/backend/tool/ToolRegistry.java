package com.example.backend.tool;

import com.example.backend.agent.AgentExecutionContext;
import com.example.backend.agent.PendingRiskConfirmationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<AgentTool> tools;

    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AgentTool tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());
            definitions.add(Map.of("type", "function", "function", function));
        }
        definitions.add(buildFinishTaskDefinition());
        return definitions;
    }

    public String execute(String toolName, Map<String, Object> arguments, AgentExecutionContext context) {
        for (AgentTool tool : tools) {
            if (tool.getName().equals(toolName)) {
                try {
                    return tool.execute(arguments, context);
                } catch (PendingRiskConfirmationException e) {
                    throw e;
                } catch (Exception e) {
                    return "ERROR: 工具执行失败: " + e.getMessage();
                }
            }
        }
        return "ERROR: 未知工具 " + toolName;
    }

    public boolean isFinishTask(String toolName) {
        return "finish_task".equals(toolName);
    }

    private Map<String, Object> buildFinishTaskDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", Map.of(
                "final_summary", Map.of(
                        "type", "string",
                        "description", "任务完成后的最终总结"
                ),
                "chart_suggest", Map.of(
                        "type", "boolean",
                        "description", "是否建议前端生成图表"
                ),
                "chart_reason", Map.of(
                        "type", "string",
                        "description", "建议或不建议图表的原因"
                ),
                "chart_time_range", Map.of(
                        "type", "string",
                        "description", "推荐图表时间范围，只能是 30m、1h、2h"
                ),
                "chart_template", Map.of(
                        "type", "string",
                        "description", "推荐图表模板，只能是 health_overview、cpu_mem_trend、anomaly_timeline、health_score_radar"
                ),
                "chart_title", Map.of(
                        "type", "string",
                        "description", "推荐图表标题"
                )
        ));
        params.put("required", List.of(
                "final_summary",
                "chart_suggest",
                "chart_reason",
                "chart_time_range",
                "chart_template",
                "chart_title"
        ));

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "finish_task");
        function.put("description", "当整个任务已经完成时调用，返回最终总结和图表建议。");
        function.put("parameters", params);
        return Map.of("type", "function", "function", function);
    }
}

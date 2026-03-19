package com.example.backend.agent;

import com.example.backend.llm.LlmClient;
import com.example.backend.model.Step;
import com.example.backend.model.StepStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final LlmClient llmClient;

    public List<Step> generatePlan(String goal, String serverIp) {
        String systemPrompt = """
                你是一名 Linux 运维任务规划器。请基于用户目标输出一个 JSON 对象，格式为：
                {"plan":[{"description":"...","hint":"...","rollbackCmd":"...","isRisky":false}]}
                规则：
                1. 只返回 JSON，不要 Markdown，不要解释。
                2. plan 保持 2 到 6 步，步骤必须原子化，优先先检查再变更。
                3. 如果任务只是查询或诊断，计划中应优先包含日志读取或监控检查步骤。
                4. 会删除数据、重启服务、修改系统配置、批量变更权限等动作，isRisky 设为 true。
                5. rollbackCmd 可以为空字符串，但有明确回滚方式时尽量提供。
                6. 不要修改登陆方式，不要修改登录端口，不要修改系统设置，除非用户特意说明
                """;

        String userPrompt = "用户目标: " + (goal == null ? "" : goal)
                + "\n目标服务器: " + (serverIp == null ? "" : serverIp);

        try {
            JsonNode root = llmClient.callForJson(systemPrompt, userPrompt);
            List<Step> parsedPlan = parsePlan(root.path("plan"), goal);
            if (!parsedPlan.isEmpty()) {
                return parsedPlan;
            }
        } catch (Exception e) {
            log.warn("Generate plan failed, fallback to single-step plan: {}", e.getMessage());
        }

        return fallbackPlan(goal);
    }

    private List<Step> parsePlan(JsonNode planNode, String goal) {
        List<Step> steps = new ArrayList<>();
        if (!planNode.isArray()) {
            return steps;
        }

        int index = 1;
        for (JsonNode node : planNode) {
            String description = node.path("description").asText("").trim();
            if (!StringUtils.hasText(description)) {
                continue;
            }

            String hint = node.path("hint").asText("").trim();
            String rollbackCmd = node.path("rollbackCmd").asText("").trim();
            boolean risky = node.path("isRisky").asBoolean(containsRiskSignal(description + " " + hint + " " + rollbackCmd));

            steps.add(Step.builder()
                    .id("step-" + index)
                    .description(description)
                    .hint(hint)
                    .rollbackCmd(rollbackCmd)
                    .risky(risky)
                    .status(StepStatus.PENDING)
                    .build());
            index++;
        }

        if (steps.isEmpty()) {
            return fallbackPlan(goal);
        }
        return steps;
    }

    private List<Step> fallbackPlan(String goal) {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.builder()
                .id("step-1")
                .description("收集与任务相关的系统现状信息")
                .hint("优先检查系统状态、服务状态、日志或监控数据，再决定下一步动作")
                .rollbackCmd("")
                .risky(false)
                .status(StepStatus.PENDING)
                .build());
        steps.add(Step.builder()
                .id("step-2")
                .description("根据检查结果执行必要的处理并验证结果")
                .hint("需要修改时先小范围验证，处理完成后给出结论")
                .rollbackCmd("")
                .risky(containsRiskSignal(goal))
                .status(StepStatus.PENDING)
                .build());
        return steps;
    }

    private boolean containsRiskSignal(String text) {
        String normalized = String.valueOf(text).toLowerCase(Locale.ROOT);
        return normalized.contains("删除")
                || normalized.contains("重启")
                || normalized.contains("回滚")
                || normalized.contains("配置")
                || normalized.contains("权限")
                || normalized.contains("清理")
                || normalized.contains("restart")
                || normalized.contains("delete")
                || normalized.contains("remove")
                || normalized.contains("chmod")
                || normalized.contains("systemctl");
    }
}

package com.example.backend.model;

import com.example.backend.llm.LlmResponse;
import com.example.backend.llm.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskState {

    private String taskId;

    private String goal;

    private String sessionId;

    @Builder.Default
    private List<Step> plan = new ArrayList<>();

    @Builder.Default
    private int currentStepIndex = 0;

    @Builder.Default
    private TaskStatus status = TaskStatus.PLANNING;

    @Builder.Default
    private List<Checkpoint> checkpoints = new ArrayList<>();

    @Builder.Default
    private List<Message> currentStepMessages = new ArrayList<>();

    @Builder.Default
    private List<LlmResponse.ToolCall> pendingToolCalls = new ArrayList<>();

    @Builder.Default
    private String finalSummary = "";

    @Builder.Default
    private boolean chartSuggest = false;

    @Builder.Default
    private String chartReason = "无需图表";

    @Builder.Default
    private String chartTimeRange = "1h";

    @Builder.Default
    private String chartTemplate = "health_overview";

    @Builder.Default
    private String chartTitle = "服务器健康总览（1h）";

    public Step currentStep() {
        if (plan == null || currentStepIndex < 0 || currentStepIndex >= plan.size()) {
            return null;
        }
        return plan.get(currentStepIndex);
    }

    public String buildContextForLlm() {
        StringBuilder sb = new StringBuilder();
        sb.append("任务目标: ").append(StringUtils.hasText(goal) ? goal : "未提供")
                .append("\n任务状态: ").append(status)
                .append("\n计划进度:");

        if (plan == null || plan.isEmpty()) {
            sb.append("\n- 暂无计划");
        } else {
            for (int index = 0; index < plan.size(); index++) {
                Step step = plan.get(index);
                sb.append("\n")
                        .append(index + 1)
                        .append(". [")
                        .append(step.getStatus())
                        .append("] ")
                        .append(StringUtils.hasText(step.getDescription()) ? step.getDescription() : "未命名步骤");
                if (StringUtils.hasText(step.getResult())) {
                    sb.append(" => ").append(abbreviate(step.getResult(), 120));
                }
            }
        }

        sb.append("\n最近 checkpoint:");
        if (checkpoints == null || checkpoints.isEmpty()) {
            sb.append("\n- 暂无 checkpoint");
        } else {
            int fromIndex = Math.max(0, checkpoints.size() - 3);
            for (int index = fromIndex; index < checkpoints.size(); index++) {
                Checkpoint checkpoint = checkpoints.get(index);
                sb.append("\n- ")
                        .append(checkpoint.getStepDescription())
                        .append(": ")
                        .append(abbreviate(checkpoint.getResult(), 160));
            }
        }

        return sb.toString();
    }

    private String abbreviate(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}

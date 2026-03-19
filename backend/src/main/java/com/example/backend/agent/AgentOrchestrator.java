package com.example.backend.agent;

import com.example.backend.llm.LlmClient;
import com.example.backend.llm.LlmResponse;
import com.example.backend.llm.Message;
import com.example.backend.model.Checkpoint;
import com.example.backend.model.Step;
import com.example.backend.model.StepStatus;
import com.example.backend.model.TaskState;
import com.example.backend.model.TaskStatus;
import com.example.backend.tool.ToolRegistry;
import com.example.backend.websocket.WsNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final int MAX_TOOL_CALLS_PER_STEP = 8;
    private static final int MAX_RETRIES_PER_STEP = 2;
    private static final String DEFAULT_CHART_RANGE = "1h";
    private static final String DEFAULT_CHART_TEMPLATE = "health_overview";

    private final PlannerService plannerService;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final WsNotifier wsNotifier;

    public TaskState runTask(AgentExecutionContext context) {
        TaskState taskState = context.getTaskState();
        if (taskState == null) {
            throw new IllegalArgumentException("TaskState 不能为空。");
        }

        ensureNotStopped(context);

        if (taskState.getPlan() == null || taskState.getPlan().isEmpty()) {
            taskState.setStatus(TaskStatus.PLANNING);
            wsNotifier.status(context.getSession(), TaskStatus.PLANNING);
            wsNotifier.progress(context.getSession(), "planning", "AI 正在生成执行计划...", elapsedMs(context));

            List<Step> plan = plannerService.generatePlan(taskState.getGoal(), context.getServerIp());
            taskState.setPlan(plan);
            taskState.setCurrentStepIndex(0);

            wsNotifier.plan(context.getSession(), plan);
            wsNotifier.progress(context.getSession(), "plan_ready", "执行计划已生成，共 " + plan.size() + " 步。", elapsedMs(context));
        }

        taskState.setStatus(TaskStatus.RUNNING);
        wsNotifier.status(context.getSession(), TaskStatus.RUNNING);

        while (taskState.currentStep() != null) {
            ensureNotStopped(context);
            if (taskState.getStatus() == TaskStatus.DONE) {
                return taskState;
            }

            Step step = taskState.currentStep();
            if (step.getStatus() == StepStatus.DONE || step.getStatus() == StepStatus.SKIPPED) {
                taskState.setCurrentStepIndex(taskState.getCurrentStepIndex() + 1);
                taskState.setCurrentStepMessages(new ArrayList<>());
                taskState.setPendingToolCalls(new ArrayList<>());
                continue;
            }

            executeCurrentStepWithRecovery(context, step);
            if (taskState.getStatus() == TaskStatus.DONE) {
                return taskState;
            }
        }

        if (!StringUtils.hasText(taskState.getFinalSummary())) {
            applyFallbackSummary(context, taskState);
        }

        taskState.setStatus(TaskStatus.DONE);
        wsNotifier.status(context.getSession(), TaskStatus.DONE);
        wsNotifier.taskDone(context.getSession(), taskState.getFinalSummary());
        return taskState;
    }

    private void executeCurrentStepWithRecovery(AgentExecutionContext context, Step step) {
        TaskState taskState = context.getTaskState();
        wsNotifier.stepStart(context.getSession(), taskState.getCurrentStepIndex() + 1, step, taskState.getPlan().size());
        wsNotifier.progress(
                context.getSession(),
                "step_start",
                "开始执行步骤 " + (taskState.getCurrentStepIndex() + 1) + ": " + step.getDescription(),
                elapsedMs(context)
        );

        while (true) {
            step.setStatus(StepStatus.RUNNING);
            try {
                executeStep(context, step);
                return;
            } catch (PendingRiskConfirmationException | AgentRoundLimitException | StopRequestedException e) {
                throw e;
            } catch (Exception e) {
                FailureDecision decision = handleStepFailure(context, step, e);
                wsNotifier.stepFailed(
                        context.getSession(),
                        step.getDescription(),
                        safeMessage(e),
                        decision.action(),
                        decision.reason()
                );

                if ("retry".equals(decision.action())) {
                    step.setRetryCount(step.getRetryCount() + 1);
                    step.setStatus(StepStatus.PENDING);
                    taskState.setCurrentStepMessages(new ArrayList<>());
                    taskState.setPendingToolCalls(new ArrayList<>());
                    wsNotifier.progress(
                            context.getSession(),
                            "step_retry",
                            "步骤失败后准备重试: " + step.getDescription(),
                            elapsedMs(context)
                    );
                    continue;
                }

                if ("skip".equals(decision.action())) {
                    step.setStatus(StepStatus.SKIPPED);
                    step.setResult(decision.reason());
                    taskState.setCurrentStepMessages(new ArrayList<>());
                    taskState.setPendingToolCalls(new ArrayList<>());
                    taskState.setCurrentStepIndex(taskState.getCurrentStepIndex() + 1);
                    wsNotifier.progress(
                            context.getSession(),
                            "step_skip",
                            "步骤已跳过: " + step.getDescription(),
                            elapsedMs(context)
                    );
                    return;
                }

                step.setStatus(StepStatus.FAILED);
                step.setResult(decision.reason());
                taskState.setStatus(TaskStatus.FAILED);
                taskState.setCurrentStepMessages(new ArrayList<>());
                taskState.setPendingToolCalls(new ArrayList<>());
                wsNotifier.status(context.getSession(), TaskStatus.FAILED);
                wsNotifier.taskFailed(context.getSession(), decision.reason());
                wsNotifier.progress(context.getSession(), "task_failed", decision.reason(), elapsedMs(context));
                throw new TaskAbortedException(decision.reason());
            }
        }
    }

    private void executeStep(AgentExecutionContext context, Step step) {
        TaskState taskState = context.getTaskState();
        List<Message> stepMessages = initializeStepMessages(context, step);
        int toolLoopCount = 0;

        while (true) {
            ensureNotStopped(context);

            if (taskState.getPendingToolCalls() != null && !taskState.getPendingToolCalls().isEmpty()) {
                processToolCalls(context, step, stepMessages, new ArrayList<>(taskState.getPendingToolCalls()));
                taskState.setPendingToolCalls(new ArrayList<>());
                if (taskState.getStatus() == TaskStatus.DONE) {
                    return;
                }
            }

            if (toolLoopCount >= MAX_TOOL_CALLS_PER_STEP) {
                throw new IllegalStateException("当前步骤工具调用次数超过限制(" + MAX_TOOL_CALLS_PER_STEP + ")。");
            }

            wsNotifier.progress(context.getSession(), "agent_think", "AI 正在决策当前步骤的下一步动作...", elapsedMs(context));
            context.consumeRound();

            LlmResponse response = llmClient.call(stepMessages, toolRegistry.getToolDefinitions());
            Message assistantMessage = response.getAssistantMessage() == null
                    ? Message.assistant(response.getContent())
                    : response.getAssistantMessage();
            stepMessages.add(assistantMessage);
            taskState.setCurrentStepMessages(new ArrayList<>(stepMessages));

            if (response.getType() == LlmResponse.ResponseType.TEXT || response.getToolCalls().isEmpty()) {
                String result = StringUtils.hasText(response.getContent()) ? response.getContent() : "步骤已完成。";
                markStepDone(context, step, result);
                return;
            }

            processToolCalls(context, step, stepMessages, response.getToolCalls());
            if (taskState.getStatus() == TaskStatus.DONE) {
                return;
            }

            toolLoopCount++;
        }
    }

    private List<Message> initializeStepMessages(AgentExecutionContext context, Step step) {
        TaskState taskState = context.getTaskState();
        if (taskState.getCurrentStepMessages() != null && !taskState.getCurrentStepMessages().isEmpty()) {
            return new ArrayList<>(taskState.getCurrentStepMessages());
        }

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildStepSystemPrompt()));

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("任务目标: ").append(taskState.getGoal())
                .append("\n目标服务器: ").append(context.getServerIp())
                .append("\n当前步骤: ").append(taskState.getCurrentStepIndex() + 1).append("/").append(taskState.getPlan().size())
                .append("\n步骤描述: ").append(step.getDescription())
                .append("\n任务上下文:\n").append(taskState.buildContextForLlm());

        if (StringUtils.hasText(step.getHint())) {
            userPrompt.append("\n执行提示: ").append(step.getHint());
        }
        if (StringUtils.hasText(step.getRollbackCmd())) {
            userPrompt.append("\n回滚命令: ").append(step.getRollbackCmd());
        }

        userPrompt.append("\n请只推进当前步骤。")
                .append("需要执行命令时调用 execute_command；")
                .append("读取日志时调用 read_log；")
                .append("查看监控时调用 get_server_metrics；")
                .append("如果整个任务已经完成，再调用 finish_task。");

        messages.add(Message.user(userPrompt.toString()));
        taskState.setCurrentStepMessages(new ArrayList<>(messages));
        return messages;
    }

    private void processToolCalls(AgentExecutionContext context,
                                  Step step,
                                  List<Message> stepMessages,
                                  List<LlmResponse.ToolCall> toolCalls) {
        TaskState taskState = context.getTaskState();
        for (int index = 0; index < toolCalls.size(); index++) {
            ensureNotStopped(context);

            LlmResponse.ToolCall toolCall = toolCalls.get(index);
            Map<String, Object> arguments = toolCall.getArguments() == null
                    ? new LinkedHashMap<>()
                    : toolCall.getArguments();

            if (toolRegistry.isFinishTask(toolCall.getName())) {
                applyFinishTask(context, step, arguments);
                taskState.setPendingToolCalls(new ArrayList<>());
                return;
            }

            try {
                notifyToolStart(context, toolCall.getName(), arguments);
                String toolResult = toolRegistry.execute(toolCall.getName(), arguments, context);
                stepMessages.add(Message.tool(toolCall.getId(), toolCall.getName(), toolResult));
                taskState.setCurrentStepMessages(new ArrayList<>(stepMessages));
                notifyToolEnd(context, toolCall.getName(), toolResult);
                wsNotifier.toolCall(
                        context.getSession(),
                        step.getDescription(),
                        toolCall.getName(),
                        arguments,
                        abbreviate(toolResult, 1200)
                );
                wsNotifier.progress(
                        context.getSession(),
                        "tool_call",
                        "工具执行完成: " + toolCall.getName(),
                        elapsedMs(context)
                );
            } catch (PendingRiskConfirmationException e) {
                taskState.setPendingToolCalls(new ArrayList<>(toolCalls.subList(index, toolCalls.size())));
                throw e;
            }
        }
    }

    private void markStepDone(AgentExecutionContext context, Step step, String result) {
        TaskState taskState = context.getTaskState();
        step.setStatus(StepStatus.DONE);
        step.setResult(result);

        taskState.getCheckpoints().add(Checkpoint.builder()
                .stepId(step.getId())
                .stepDescription(step.getDescription())
                .timestamp(System.currentTimeMillis())
                .result(result)
                .systemSnapshot(buildSystemSnapshot(context))
                .build());

        taskState.setCurrentStepMessages(new ArrayList<>());
        taskState.setPendingToolCalls(new ArrayList<>());
        taskState.setCurrentStepIndex(taskState.getCurrentStepIndex() + 1);

        wsNotifier.stepDone(context.getSession(), taskState.getCurrentStepIndex(), result);
        wsNotifier.progress(context.getSession(), "step_done", "步骤已完成: " + step.getDescription(), elapsedMs(context));
    }

    private FailureDecision handleStepFailure(AgentExecutionContext context, Step step, Exception exception) {
        if (step.isRisky() && step.getRetryCount() > 0) {
            return new FailureDecision("abort", "高风险步骤失败且已经重试过一次，终止任务。");
        }

        String systemPrompt = """
                你是一名运维恢复决策助手。请根据步骤失败信息，只返回 JSON：
                {"action":"retry|skip|abort","reason":"..."}
                规则：
                1. action 只能是 retry、skip、abort。
                2. 高风险步骤更偏向 abort。
                3. 如果错误明显属于暂时性或命令可修正，优先 retry。
                4. 如果步骤非关键且失败后仍可继续，允许 skip。
                """;

        String userPrompt = "任务目标: " + context.getTaskState().getGoal()
                + "\n当前步骤: " + step.getDescription()
                + "\n步骤提示: " + step.getHint()
                + "\n已重试次数: " + step.getRetryCount()
                + "\n错误信息: " + safeMessage(exception)
                + "\n任务上下文:\n" + context.getTaskState().buildContextForLlm();

        try {
            JsonNode root = llmClient.callForJson(systemPrompt, userPrompt);
            String action = root.path("action").asText("abort").trim().toLowerCase(Locale.ROOT);
            String reason = root.path("reason").asText("步骤执行失败，任务终止。").trim();

            if (!"retry".equals(action) && !"skip".equals(action) && !"abort".equals(action)) {
                action = "abort";
            }
            if ("retry".equals(action) && step.getRetryCount() >= MAX_RETRIES_PER_STEP) {
                action = "abort";
                reason = "已达到最大重试次数，终止任务。";
            }
            return new FailureDecision(action, StringUtils.hasText(reason) ? reason : "步骤执行失败，任务终止。");
        } catch (Exception e) {
            if (step.getRetryCount() < MAX_RETRIES_PER_STEP) {
                return new FailureDecision("retry", "恢复决策解析失败，按默认策略重试当前步骤。");
            }
            return new FailureDecision("abort", "恢复决策解析失败且已达到最大重试次数，终止任务。");
        }
    }

    private void applyFinishTask(AgentExecutionContext context, Step step, Map<String, Object> arguments) {
        TaskState taskState = context.getTaskState();
        fillSummaryFields(taskState, arguments, context.isMetricsRequested());

        if (step.getStatus() != StepStatus.DONE) {
            step.setStatus(StepStatus.DONE);
            step.setResult(taskState.getFinalSummary());
            taskState.getCheckpoints().add(Checkpoint.builder()
                    .stepId(step.getId())
                    .stepDescription(step.getDescription())
                    .timestamp(System.currentTimeMillis())
                    .result(taskState.getFinalSummary())
                    .systemSnapshot(buildSystemSnapshot(context))
                    .build());
        }

        for (int index = taskState.getCurrentStepIndex() + 1; index < taskState.getPlan().size(); index++) {
            Step remain = taskState.getPlan().get(index);
            if (remain.getStatus() == StepStatus.PENDING || remain.getStatus() == StepStatus.RUNNING) {
                remain.setStatus(StepStatus.SKIPPED);
                remain.setResult("任务已提前完成，无需继续执行。");
            }
        }

        taskState.setCurrentStepIndex(taskState.getPlan().size());
        taskState.setCurrentStepMessages(new ArrayList<>());
        taskState.setPendingToolCalls(new ArrayList<>());
        taskState.setStatus(TaskStatus.DONE);

        wsNotifier.status(context.getSession(), TaskStatus.DONE);
        wsNotifier.taskDone(context.getSession(), taskState.getFinalSummary());
        wsNotifier.progress(context.getSession(), "task_done", taskState.getFinalSummary(), elapsedMs(context));
    }

    private void applyFallbackSummary(AgentExecutionContext context, TaskState taskState) {
        String systemPrompt = """
                你是一名运维总结助手。请基于任务结果返回 JSON：
                {
                  "final_summary":"...",
                  "chart_suggest":true,
                  "chart_reason":"...",
                  "chart_time_range":"30m|1h|2h",
                  "chart_template":"health_overview|cpu_mem_trend|anomaly_timeline|health_score_radar",
                  "chart_title":"..."
                }
                如果没有图表价值，chart_suggest 返回 false。
                """;

        String userPrompt = "任务目标: " + taskState.getGoal()
                + "\n任务上下文:\n" + taskState.buildContextForLlm();

        try {
            JsonNode root = llmClient.callForJson(systemPrompt, userPrompt);
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("final_summary", root.path("final_summary").asText(""));
            arguments.put("chart_suggest", root.path("chart_suggest").asBoolean(false));
            arguments.put("chart_reason", root.path("chart_reason").asText(""));
            arguments.put("chart_time_range", root.path("chart_time_range").asText(DEFAULT_CHART_RANGE));
            arguments.put("chart_template", root.path("chart_template").asText(DEFAULT_CHART_TEMPLATE));
            arguments.put("chart_title", root.path("chart_title").asText(""));
            fillSummaryFields(taskState, arguments, context.isMetricsRequested());
        } catch (Exception e) {
            String summary = "任务已完成。";
            if (!taskState.getCheckpoints().isEmpty()) {
                summary = taskState.getCheckpoints().get(taskState.getCheckpoints().size() - 1).getResult();
            }
            taskState.setFinalSummary(summary);
            boolean chartSuggest = context.isMetricsRequested() && containsChartSignal(summary);
            taskState.setChartSuggest(chartSuggest);
            taskState.setChartReason(chartSuggest ? "结果包含监控与趋势信息，建议配合图表展示。" : "文本总结已足够表达结果，无需图表。");
            taskState.setChartTimeRange(DEFAULT_CHART_RANGE);
            taskState.setChartTemplate(DEFAULT_CHART_TEMPLATE);
            taskState.setChartTitle(defaultChartTitle(DEFAULT_CHART_TEMPLATE, DEFAULT_CHART_RANGE));
        }
    }

    private void fillSummaryFields(TaskState taskState, Map<String, Object> arguments, boolean metricsRequested) {
        String summary = String.valueOf(arguments.getOrDefault("final_summary", ""));
        if (!StringUtils.hasText(summary)) {
            summary = "任务已完成。";
        }
        taskState.setFinalSummary(summary);

        boolean chartSuggest = arguments.containsKey("chart_suggest")
                ? toBoolean(arguments.get("chart_suggest"))
                : metricsRequested && containsChartSignal(summary);

        String chartTemplate = normalizeChartTemplate(String.valueOf(arguments.getOrDefault("chart_template", DEFAULT_CHART_TEMPLATE)));
        String chartTimeRange = normalizeChartTimeRange(String.valueOf(arguments.getOrDefault("chart_time_range", DEFAULT_CHART_RANGE)));
        String chartReason = String.valueOf(arguments.getOrDefault("chart_reason", ""));
        String chartTitle = String.valueOf(arguments.getOrDefault("chart_title", ""));

        if (!StringUtils.hasText(chartReason)) {
            chartReason = chartSuggest
                    ? "结果包含趋势、峰值、波动或异常信息，建议配合图表展示。"
                    : "文本总结已足够表达处理结果，无需额外图表。";
        }
        if (!StringUtils.hasText(chartTitle)) {
            chartTitle = defaultChartTitle(chartTemplate, chartTimeRange);
        }

        taskState.setChartSuggest(chartSuggest);
        taskState.setChartReason(chartReason);
        taskState.setChartTimeRange(chartTimeRange);
        taskState.setChartTemplate(chartTemplate);
        taskState.setChartTitle(chartTitle);
    }

    private Map<String, String> buildSystemSnapshot(AgentExecutionContext context) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("serverIp", String.valueOf(context.getServerIp()));
        snapshot.put("usedRounds", String.valueOf(context.getUsedRounds()));
        snapshot.put("metricsRequested", String.valueOf(context.isMetricsRequested()));
        return snapshot;
    }

    private String buildStepSystemPrompt() {
        return """
                你是一名资深 Linux 运维 Agent，正在执行一份已经规划好的步骤。
                规则：
                1. 所有可执行动作都必须通过工具 execute_command，不要假装执行。
                2. 读取日志优先使用 read_log；查看监控优先使用 get_server_metrics。
                3. 当前只关注当前步骤，不要重复已经完成的步骤。
                4. 工具返回 ERROR 时，不要立刻放弃，应结合错误信息调整做法。
                5. 如果当前步骤完成，直接输出文本总结即可。
                6. 只有整个任务已经完成时，才调用 finish_task。
                7. 包管理命令必须使用非交互参数，例如 apt/yum/dnf 要加 -y。
                """;
    }

    private void notifyToolStart(AgentExecutionContext context, String toolName, Map<String, Object> arguments) {
        if ("execute_command".equals(toolName)) {
            wsNotifier.progress(
                    context.getSession(),
                    "cmd_exec_start",
                    "执行命令: " + String.valueOf(arguments.getOrDefault("command", "")),
                    elapsedMs(context)
            );
            return;
        }

        if ("read_log".equals(toolName)) {
            wsNotifier.progress(
                    context.getSession(),
                    "log_read_start",
                    "开始读取日志: " + String.valueOf(arguments.getOrDefault("file_path", "")),
                    elapsedMs(context)
            );
            return;
        }

        if ("get_server_metrics".equals(toolName)) {
            wsNotifier.progress(
                    context.getSession(),
                    "metrics_fetch_start",
                    "开始获取监控数据: " + String.valueOf(arguments.getOrDefault("serverIp", context.getServerIp())),
                    elapsedMs(context)
            );
        }
    }

    private void notifyToolEnd(AgentExecutionContext context, String toolName, String toolResult) {
        if ("execute_command".equals(toolName)) {
            String stage = toolResult != null && toolResult.contains("\"success\":true") ? "cmd_exec_done" : "cmd_exec_fail";
            wsNotifier.progress(context.getSession(), stage, abbreviate(toolResult, 600), elapsedMs(context));
            return;
        }

        if ("read_log".equals(toolName)) {
            wsNotifier.progress(context.getSession(), "log_read_done", abbreviate(toolResult, 600), elapsedMs(context));
            return;
        }

        if ("get_server_metrics".equals(toolName)) {
            wsNotifier.progress(context.getSession(), "metrics_fetch_done", abbreviate(toolResult, 600), elapsedMs(context));
        }
    }

    private void ensureNotStopped(AgentExecutionContext context) {
        if (context.shouldStop()) {
            throw new StopRequestedException("任务已被用户强制停止。");
        }
    }

    private long elapsedMs(AgentExecutionContext context) {
        return System.currentTimeMillis() - context.getStartedAt();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean containsChartSignal(String text) {
        String content = String.valueOf(text).toLowerCase(Locale.ROOT);
        return content.contains("cpu")
                || content.contains("内存")
                || content.contains("负载")
                || content.contains("趋势")
                || content.contains("波动")
                || content.contains("峰值")
                || content.contains("异常")
                || content.contains("监控");
    }

    private String normalizeChartTimeRange(String timeRange) {
        String normalized = String.valueOf(timeRange).trim().toLowerCase(Locale.ROOT);
        if ("30m".equals(normalized) || "1h".equals(normalized) || "2h".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_CHART_RANGE;
    }

    private String normalizeChartTemplate(String chartTemplate) {
        String normalized = String.valueOf(chartTemplate).trim().toLowerCase(Locale.ROOT);
        if ("cpu_mem_trend".equals(normalized)
                || "anomaly_timeline".equals(normalized)
                || "health_score_radar".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_CHART_TEMPLATE;
    }

    private String defaultChartTitle(String chartTemplate, String timeRange) {
        String prefix = switch (chartTemplate) {
            case "cpu_mem_trend" -> "CPU / 内存趋势图";
            case "anomaly_timeline" -> "异常时序图";
            case "health_score_radar" -> "健康评分雷达图";
            default -> "服务器健康总览";
        };
        return prefix + "（" + (StringUtils.hasText(timeRange) ? timeRange : DEFAULT_CHART_RANGE) + "）";
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String safeMessage(Exception exception) {
        if (exception == null || !StringUtils.hasText(exception.getMessage())) {
            return "未知错误";
        }
        return exception.getMessage();
    }

    private record FailureDecision(String action, String reason) {
    }
}

package com.example.backend.service;

import com.example.backend.agent.AgentExecutionContext;
import com.example.backend.agent.AgentOrchestrator;
import com.example.backend.agent.AgentRoundLimitException;
import com.example.backend.agent.PendingRiskConfirmationException;
import com.example.backend.agent.StopRequestedException;
import com.example.backend.model.TaskState;
import com.example.backend.model.TaskStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAgentService {

    private static final int DEFAULT_AGENT_ROUNDS = 15;
    private static final String DEFAULT_CHART_RANGE = "1h";
    private static final String DEFAULT_CHART_TEMPLATE = "health_overview";

    private final AgentOrchestrator agentOrchestrator;
    private final CommandSafetyService commandSafetyService;

    private final ConcurrentHashMap<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    @Getter
    @Setter
    public static class AgentRunResult {
        private String finalSummary;
        private boolean stopped;
        private boolean chartSuggest;
        private String chartReason;
        private String chartTimeRange;
        private String chartTemplate;
        private String chartTitle;

        public static AgentRunResult defaults() {
            AgentRunResult result = new AgentRunResult();
            result.setFinalSummary("");
            result.setStopped(false);
            result.setChartSuggest(false);
            result.setChartReason("无需图表");
            result.setChartTimeRange(DEFAULT_CHART_RANGE);
            result.setChartTemplate(DEFAULT_CHART_TEMPLATE);
            result.setChartTitle(defaultChartTitle(DEFAULT_CHART_TEMPLATE, DEFAULT_CHART_RANGE));
            return result;
        }

        private static String defaultChartTitle(String chartTemplate, String timeRange) {
            String prefix = switch (chartTemplate) {
                case "cpu_mem_trend" -> "CPU / 内存趋势图";
                case "anomaly_timeline" -> "异常时序图";
                case "health_score_radar" -> "健康评分雷达图";
                default -> "服务器健康总览";
            };
            return prefix + "（" + (StringUtils.hasText(timeRange) ? timeRange : DEFAULT_CHART_RANGE) + "）";
        }
    }

    public void stopAgent(String sessionId) {
        AtomicBoolean flag = stopFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
        }
    }

    public boolean isHighRiskCommand(String command) {
        return commandSafetyService.isHighRiskCommand(command);
    }

    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, DEFAULT_AGENT_ROUNDS, null, session)
                .getFinalSummary();
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, DEFAULT_AGENT_ROUNDS, null, session);
    }

    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               int maxRounds,
                               String approvedRiskCommand,
                               WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, maxRounds, approvedRiskCommand, session)
                .getFinalSummary();
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 int maxRounds,
                                                 String approvedRiskCommand,
                                                 WebSocketSession session) {
        return runAgentLoopWithAdvice(
                userQuery,
                serverIp,
                username,
                password,
                maxRounds,
                approvedRiskCommand,
                session,
                null
        );
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 int maxRounds,
                                                 String approvedRiskCommand,
                                                 WebSocketSession session,
                                                 TaskState existingTaskState) {
        AgentRunResult result = AgentRunResult.defaults();
        String sessionId = session.getId();
        stopFlags.put(sessionId, new AtomicBoolean(false));

        TaskState taskState = existingTaskState != null ? existingTaskState : createTaskState(userQuery, sessionId);
        mergeSupplementalQuery(taskState, userQuery);

        AgentExecutionContext context = AgentExecutionContext.builder()
                .serverIp(serverIp)
                .username(username)
                .password(password)
                .approvedRiskCommand(approvedRiskCommand)
                .session(session)
                .taskState(taskState)
                .maxRounds(Math.max(1, maxRounds))
                .startedAt(System.currentTimeMillis())
                .stopSignal(() -> shouldStop(sessionId))
                .build();

        try {
            TaskState finishedState = agentOrchestrator.runTask(context);
            fillFinalResult(result, finishedState);
            return result;
        } catch (PendingRiskConfirmationException e) {
            taskState.setStatus(TaskStatus.PAUSED);
            throw new HighRiskCommandException(e.getCommand(), e.getReason(), taskState);
        } catch (AgentRoundLimitException e) {
            taskState.setStatus(TaskStatus.PAUSED);
            throw new AgentTimeoutException(e.getMessage(), taskState);
        } catch (StopRequestedException e) {
            result.setStopped(true);
            result.setFinalSummary("任务已被用户强制停止。");
            return result;
        } finally {
            stopFlags.remove(sessionId);
        }
    }

    private boolean shouldStop(String sessionId) {
        AtomicBoolean flag = stopFlags.get(sessionId);
        return Thread.currentThread().isInterrupted() || (flag != null && flag.get());
    }

    private TaskState createTaskState(String userQuery, String sessionId) {
        TaskState taskState = TaskState.builder()
                .taskId(UUID.randomUUID().toString())
                .goal(userQuery == null ? "" : userQuery)
                .sessionId(sessionId)
                .plan(new ArrayList<>())
                .checkpoints(new ArrayList<>())
                .currentStepMessages(new ArrayList<>())
                .pendingToolCalls(new ArrayList<>())
                .status(TaskStatus.PLANNING)
                .chartSuggest(false)
                .chartReason("无需图表")
                .chartTimeRange(DEFAULT_CHART_RANGE)
                .chartTemplate(DEFAULT_CHART_TEMPLATE)
                .chartTitle(AgentRunResult.defaultChartTitle(DEFAULT_CHART_TEMPLATE, DEFAULT_CHART_RANGE))
                .build();
        taskState.setFinalSummary("");
        return taskState;
    }

    private void mergeSupplementalQuery(TaskState taskState, String userQuery) {
        if (taskState == null || !StringUtils.hasText(userQuery)) {
            return;
        }

        String normalized = userQuery.trim();
        if ("继续".equals(normalized)
                || "continue".equalsIgnoreCase(normalized)
                || "[继续执行]".equals(normalized)
                || "[高风险确认继续执行]".equals(normalized)) {
            return;
        }

        if (!StringUtils.hasText(taskState.getGoal())) {
            taskState.setGoal(normalized);
            return;
        }

        if (!taskState.getGoal().contains(normalized)) {
            taskState.setGoal(taskState.getGoal() + "\n补充要求: " + normalized);
        }
    }

    private void fillFinalResult(AgentRunResult result, TaskState taskState) {
        result.setFinalSummary(StringUtils.hasText(taskState.getFinalSummary()) ? taskState.getFinalSummary() : "任务已结束。");
        result.setChartSuggest(taskState.isChartSuggest());
        result.setChartReason(taskState.getChartReason());
        result.setChartTimeRange(taskState.getChartTimeRange());
        result.setChartTemplate(taskState.getChartTemplate());
        result.setChartTitle(taskState.getChartTitle());
    }

    @Getter
    public static class HighRiskCommandException extends RuntimeException {
        private final String command;
        private final String reason;
        private final TaskState taskState;

        public HighRiskCommandException(String command, String reason, TaskState taskState) {
            super(reason);
            this.command = command;
            this.reason = reason;
            this.taskState = taskState;
        }
    }

    @Getter
    public static class AgentTimeoutException extends RuntimeException {
        private final TaskState taskState;

        public AgentTimeoutException(String message, TaskState taskState) {
            super(message);
            this.taskState = taskState;
        }
    }
}

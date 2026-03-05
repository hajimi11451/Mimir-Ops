package com.example.backend.handler;

import com.example.backend.service.MonitorService;
import com.example.backend.service.OpsAgentService;
import com.example.backend.utils.AiUtils;
import com.example.backend.utils.SshUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ServerWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> AGENT_HISTORY = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private OpsAgentService opsAgentService;

    @Autowired
    private MonitorService monitorService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());
        SESSIONS.put(session.getId(), session);

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "welcome");
        msg.put("message", "Connection established. Send type=ops_chat for AI assistant.");
        sendJson(session, msg);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("WS recv [{}]: {}", session.getId(), payload);

        if ("ping".equalsIgnoreCase(payload)) {
            sendText(session, "pong");
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");
            if ("ops_chat".equals(type)) {
                handleOpsChat(session, node);
                return;
            }
            if ("ops_force_stop".equals(type)) {
                handleOpsForceStop(session, node);
                return;
            }
            if ("risk_execute".equals(type)) {
                handleRiskExecute(session, node);
                return;
            }
            if ("chart_data_request".equals(type)) {
                handleChartDataRequest(session, node);
                return;
            }

            Map<String, Object> fallback = new HashMap<>();
            fallback.put("type", "error");
            fallback.put("message", "unsupported type, use type=ops_chat/risk_execute/chart_data_request");
            sendJson(session, fallback);
        } catch (Exception ex) {
            // plain text fallback: regular AI chat
            String answer = aiUtils.chatWithOpsAssistant(payload);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "chat_result");
            data.put("reply", answer);
            sendJson(session, data);
        }
    }

    private void handleOpsChat(WebSocketSession session, JsonNode node) {
        long start = System.currentTimeMillis();
        String query = node.path("query").asText("");
        boolean execute = node.path("execute").asBoolean(false);
        int maxRounds = node.path("maxRounds").asInt(15);
        if (maxRounds < 1) maxRounds = 1;
        if (maxRounds > 50) maxRounds = 50;

        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");

        log.info("ops_chat start, sessionId={}, execute={}, serverIp={}, queryLength={}, maxRounds={}",
                session.getId(), execute, serverIp, query == null ? 0 : query.length(), maxRounds);
        sendProgress(session, "start", "收到请求，开始进入 Agent 自主执行...", 0);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", query);

        if (!execute) {
            result.put("executed", false);
            result.put("reply", "当前请求未开启执行模式（execute=false），未启动 Agent。");
            result.put("execResult", "未执行。若要启动自主 Agent，请携带 execute=true。");
            appendChartAdvice(query, result);
            sendJson(session, result);
            sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
            return;
        }

        // 如果用户发送“继续”，且有历史记录，则恢复执行
        if ("继续".equals(query) || "continue".equalsIgnoreCase(query)) {
            List<Map<String, Object>> history = AGENT_HISTORY.get(session.getId());
            if (history != null && !history.isEmpty()) {
                log.info("Resuming agent loop from history, sessionId={}", session.getId());
                try {
                    String finalSummary = opsAgentService.runAgentLoop(
                        "[继续执行]", serverIp, username, password, maxRounds, null, session, history
                    );
                    result.put("executed", true);
                    result.put("reply", finalSummary);
                    result.put("execResult", "Agent 循环已完成。");
                } catch (OpsAgentService.HighRiskCommandException e) {
                    AGENT_HISTORY.put(session.getId(), e.getHistory());
                    result.put("executed", false);
                    result.put("needRiskConfirm", true);
                    result.put("riskLevel", "high");
                    result.put("riskCommand", e.getCommand());
                    result.put("reply", e.getReason());
                    result.put("execResult", "检测到高风险命令，等待用户确认。");
                } catch (OpsAgentService.AgentTimeoutException e) {
                    AGENT_HISTORY.put(session.getId(), e.getHistory());
                    result.put("executed", false);
                    result.put("timeout", true);
                    result.put("reply", e.getMessage());
                    result.put("execResult", "再次达到最大轮数，任务暂停。");
                    sendProgress(session, "agent_timeout", e.getMessage(), System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.error("Resumed agent loop failed", e);
                    result.put("reply", "恢复执行失败: " + e.getMessage());
                }
                sendJson(session, result);
                sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
                return;
            }
        }

        if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            result.put("executed", false);
            result.put("reply", "缺少 serverIp/username/password，无法启动 Agent。");
            result.put("execResult", "参数不足。");
            appendChartAdvice(query, result);
            sendJson(session, result);
            sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
            return;
        }

        try {
            // 清除旧历史
            AGENT_HISTORY.remove(session.getId());
            String finalSummary = opsAgentService.runAgentLoop(query, serverIp, username, password, maxRounds, null, session);
            result.put("executed", true);
            result.put("reply", finalSummary);
            result.put("execResult", "Agent 循环已完成，请参考上方实时进度日志。");
        } catch (OpsAgentService.HighRiskCommandException e) {
            // 保存历史上下文
            AGENT_HISTORY.put(session.getId(), e.getHistory());
            
            result.put("executed", false);
            result.put("needRiskConfirm", true);
            result.put("riskLevel", "high");
            result.put("riskCommand", e.getCommand());
            result.put("reply", e.getReason());
            result.put("execResult", "检测到高风险命令，等待用户确认。");
        } catch (OpsAgentService.AgentTimeoutException e) {
             // 超时，保存上下文以便继续
            AGENT_HISTORY.put(session.getId(), e.getHistory());

            result.put("executed", false);
            result.put("timeout", true);
            result.put("reply", e.getMessage());
            result.put("execResult", "达到最大轮数，任务暂停。你可以发送“继续”来恢复执行。");
            sendProgress(session, "agent_timeout", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("ops_chat agent loop failed, sessionId={}", session.getId(), e);
            result.put("executed", false);
            result.put("reply", "Agent 执行失败");
            result.put("execResult", e.getMessage());
        }

        appendChartAdvice(query, result);
        sendJson(session, result);
        sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
        log.info("ops_chat finished, sessionId={}, totalElapsedMs={}", session.getId(), System.currentTimeMillis() - start);
    }

    private void handleOpsForceStop(WebSocketSession session, JsonNode node) {
        log.info("ops_force_stop received, sessionId={}", session.getId());
        
        // 1. 如果 Agent 正在运行 loop，stopAgent 会设置 flag，loop 内部检测到后会停止
        opsAgentService.stopAgent(session.getId());
        
        // 2. 如果 Agent 处于“等待用户确认高风险命令”或“超时等待继续”的状态（即 history 不为空），
        //    说明 loop 已经退出了，此时需要手动清理状态并发送停止消息。
        if (AGENT_HISTORY.containsKey(session.getId())) {
            AGENT_HISTORY.remove(session.getId());
            Map<String, Object> progress = new HashMap<>();
            progress.put("type", "ops_progress");
            progress.put("stage", "agent_stopped");
            progress.put("message", "任务已被用户强制停止（在等待确认阶段）。");
            sendJson(session, progress);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_stop_ack");
        result.put("message", "已发送强制停止请求...");
        sendJson(session, result);
    }

    /**
     * 用户点击高风险确认后的执行入口。
     */
    private void handleRiskExecute(WebSocketSession session, JsonNode node) {
        long start = System.currentTimeMillis();
        String command = node.path("command").asText("");
        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", "[高风险确认执行]");

        if (!StringUtils.hasText(command)) {
            result.put("executed", false);
            result.put("reply", "缺少命令，无法执行。");
            result.put("execResult", "risk_execute.command 为空");
            sendJson(session, result);
            return;
        }

        if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            result.put("executed", false);
            result.put("reply", "缺少 serverIp/username/password，无法执行。 ");
            result.put("execResult", "参数不足。");
            sendJson(session, result);
            return;
        }

        try {
            sendProgress(session, "risk_exec_start", "用户已确认，开始执行高风险命令...", System.currentTimeMillis() - start);
            
            // 获取并移除历史上下文
            List<Map<String, Object>> history = AGENT_HISTORY.remove(session.getId());
            if (history == null) {
                history = new ArrayList<>();
            }
            
            // 继续执行 Agent 循环，传入 history 和 approvedRiskCommand
            String finalSummary = opsAgentService.runAgentLoop(
                "[高风险确认继续执行]", 
                serverIp, 
                username, 
                password, 
                15, // 默认剩余轮数，也可以保存之前的轮数
                command, 
                session, 
                history
            );

            result.put("executed", true);
            result.put("reply", finalSummary);
            result.put("execResult", "高风险命令执行并后续流程已完成。");
            sendProgress(session, "risk_exec_done", "高风险命令及后续流程执行完成", System.currentTimeMillis() - start);
        } catch (OpsAgentService.HighRiskCommandException e) {
             // 再次遇到高风险命令（可能是新的）
            AGENT_HISTORY.put(session.getId(), e.getHistory());
            
            result.put("executed", false);
            result.put("needRiskConfirm", true);
            result.put("riskLevel", "high");
            result.put("riskCommand", e.getCommand());
            result.put("reply", e.getReason());
            result.put("execResult", "再次检测到高风险命令，等待用户确认。");
        } catch (Exception e) {
            log.error("risk_execute failed, sessionId={}", session.getId(), e);
            result.put("executed", false);
            result.put("reply", "高风险命令执行失败");
            result.put("execResult", e.getMessage());
        }

        appendChartAdvice("[高风险确认执行]", result);
        sendJson(session, result);
    }

    /**
     * 前端点击“生成图表”后请求图表数据。
     */
    private void handleChartDataRequest(WebSocketSession session, JsonNode node) {
        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");
        String timeRange = node.path("timeRange").asText("1h");
        String chartTemplate = node.path("chartTemplate").asText("health_overview");

        Map<String, Object> result = new HashMap<>();
        result.put("type", "chart_data_result");
        result.put("success", false);

        if (!StringUtils.hasText(serverIp)) {
            result.put("message", "缺少 serverIp，无法生成图表。");
            sendJson(session, result);
            return;
        }

        try {
            Map<String, Object> metrics = monitorService.getMetrics(serverIp, timeRange);
            int historyPoints = 0;
            try {
                historyPoints = Integer.parseInt(String.valueOf(metrics.get("historyPoints")));
            } catch (Exception ignored) {
            }

            if (historyPoints <= 0 && StringUtils.hasText(username) && StringUtils.hasText(password)) {
                Map<String, Object> sampleResult = monitorService.sampleMetricsOnce(serverIp, username, password);
                metrics = monitorService.getMetrics(serverIp, timeRange);
                metrics.put("sampleOnceWhenEmpty", sampleResult);
            }

            result.put("success", true);
            result.put("message", "图表数据已生成。");
            result.put("chartTemplate", chartTemplate);
            result.put("chartData", buildChartPayload(chartTemplate, metrics));
            sendJson(session, result);
        } catch (Exception e) {
            result.put("message", "生成图表数据失败: " + e.getMessage());
            sendJson(session, result);
        }
    }

    private void appendChartAdvice(String query, Map<String, Object> result) {
        try {
            Map<String, Object> chartAdvice = aiUtils.analyzeChartNeed(
                    query,
                    String.valueOf(result.getOrDefault("reply", "")),
                    String.valueOf(result.getOrDefault("execResult", ""))
            );
            result.putAll(chartAdvice);
        } catch (Exception ignored) {
            result.put("chartSuggest", false);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildChartPayload(String chartTemplate, Map<String, Object> metrics) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("template", chartTemplate);
        payload.put("timeRange", metrics.getOrDefault("timeRange", "1h"));
        payload.put("serverIp", metrics.getOrDefault("serverIp", ""));
        payload.put("history", metrics.getOrDefault("history", List.of()));
        payload.put("summary", metrics.getOrDefault("summary", Map.of()));
        payload.put("current", metrics.getOrDefault("current", Map.of()));

        List<Map<String, Object>> history = (List<Map<String, Object>>) payload.get("history");
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (Map<String, Object> p : history) {
            double cpu = toDouble(p.get("cpuUsage"));
            double mem = toDouble(p.get("memUsage"));
            if (cpu >= 85 || mem >= 85) {
                Map<String, Object> a = new HashMap<>();
                a.put("time", p.getOrDefault("time", ""));
                a.put("cpuUsage", cpu);
                a.put("memUsage", mem);
                a.put("level", cpu >= 95 || mem >= 95 ? "critical" : "warning");
                anomalies.add(a);
            }
        }
        payload.put("anomalies", anomalies);

        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        double avgCpu = toDouble(summary.get("avgCpu"));
        double maxCpu = toDouble(summary.get("maxCpu"));
        double avgMem = toDouble(summary.get("avgMem"));
        double maxMem = toDouble(summary.get("maxMem"));
        double cpuScore = scoreByUsage(avgCpu, maxCpu);
        double memScore = scoreByUsage(avgMem, maxMem);
        double stabilityScore = Math.max(0, 100 - anomalies.size() * 5.0);
        double capacityScore = Math.max(0, 100 - Math.max(maxCpu, maxMem));
        Map<String, Object> healthScores = new HashMap<>();
        healthScores.put("cpuScore", round1(cpuScore));
        healthScores.put("memScore", round1(memScore));
        healthScores.put("stabilityScore", round1(stabilityScore));
        healthScores.put("capacityScore", round1(capacityScore));
        healthScores.put("overall", round1((cpuScore + memScore + stabilityScore + capacityScore) / 4.0));
        payload.put("healthScores", healthScores);
        return payload;
    }

    private double scoreByUsage(double avg, double max) {
        double penalty = avg * 0.5 + max * 0.5;
        return Math.max(0, 100 - penalty);
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket closed: {}", session.getId());
        SESSIONS.remove(session.getId());
        AGENT_HISTORY.remove(session.getId());
    }

    private void sendText(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(msg));
            }
        } catch (IOException e) {
            log.error("send text failed", e);
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.error("send json failed", e);
        }
    }

    private void sendProgress(WebSocketSession session, String stage, String message, long elapsedMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ops_progress");
        payload.put("stage", stage);
        payload.put("message", message);
        payload.put("elapsedMs", elapsedMs);
        sendJson(session, payload);
    }
}

package com.example.backend.websocket;

import com.example.backend.model.Step;
import com.example.backend.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsNotifier {

    private final ObjectMapper objectMapper;

    public void progress(WebSocketSession session, String stage, String message, long elapsedMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ops_progress");
        payload.put("stage", stage);
        payload.put("message", message);
        payload.put("elapsedMs", elapsedMs);
        send(session, payload);
    }

    public void status(WebSocketSession session, TaskStatus status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "status");
        payload.put("status", status.name());
        send(session, payload);
    }

    public void plan(WebSocketSession session, List<Step> plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "plan");
        payload.put("plan", plan == null ? List.of() : plan.stream().map(step -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("description", step.getDescription());
            item.put("isRisky", step.isRisky());
            item.put("hasRollback", step.getRollbackCmd() != null && !step.getRollbackCmd().isBlank());
            return item;
        }).toList());
        send(session, payload);
    }

    public void stepStart(WebSocketSession session, int index, Step step, int total) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "step_start");
        payload.put("index", index);
        payload.put("description", step == null ? "" : step.getDescription());
        payload.put("total", total);
        send(session, payload);
    }

    public void stepDone(WebSocketSession session, int index, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "step_done");
        payload.put("index", index);
        payload.put("result", result);
        send(session, payload);
    }

    public void stepFailed(WebSocketSession session,
                           String description,
                           String error,
                           String action,
                           String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "step_failed");
        payload.put("description", description);
        payload.put("error", error);
        payload.put("action", action);
        payload.put("reason", reason);
        send(session, payload);
    }

    public void toolCall(WebSocketSession session, String step, String tool, Map<String, Object> args, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_call");
        payload.put("step", step);
        payload.put("tool", tool);
        payload.put("args", args);
        payload.put("result", result);
        send(session, payload);
    }

    public void taskDone(WebSocketSession session, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "task_done");
        payload.put("summary", summary);
        send(session, payload);
    }

    public void taskFailed(WebSocketSession session, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "task_failed");
        payload.put("error", error);
        send(session, payload);
    }

    public void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session == null || !session.isOpen()) {
                return;
            }
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket notify failed: {}", e.getMessage());
        }
    }
}

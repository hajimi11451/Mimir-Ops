package com.example.backend.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器日志/状态 WebSocket 处理器
 */
@Slf4j
@Component
public class ServerWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket 连接建立: {}", session.getId());
        SESSIONS.put(session.getId(), session);
        
        // 发送欢迎消息，模拟连接成功
        sendMessage(session, "Connection established. Ready to stream server logs.");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到消息 [{}]: {}", session.getId(), payload);

        // 简单的 Echo 逻辑或命令处理
        if ("ping".equalsIgnoreCase(payload)) {
            sendMessage(session, "pong");
        } else {
            sendMessage(session, "Server received: " + payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 连接断开: {}", session.getId());
        SESSIONS.remove(session.getId());
    }

    private void sendMessage(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(msg));
            }
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }
}

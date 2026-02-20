package com.example.backend;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketTest {

    @LocalServerPort
    private int port;

    @Test
    public void testServerConnection() throws Exception {
        WebSocketClient client = new StandardWebSocketClient();
        String url = "ws://localhost:" + port + "/ws/server/connect";
        
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        WebSocketSession session = client.doHandshake(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                log.info("Client connected");
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                log.info("Client received: {}", message.getPayload());
                messages.offer(message.getPayload());
            }
        }, url).get(5, TimeUnit.SECONDS);

        Assertions.assertTrue(session.isOpen(), "WebSocket connection should be open");

        // 1. Verify welcome message
        String welcomeMsg = messages.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(welcomeMsg, "Should receive welcome message");
        Assertions.assertTrue(welcomeMsg.contains("Connection established"), "Welcome message mismatch");

        // 2. Test Ping-Pong
        session.sendMessage(new TextMessage("ping"));
        String response = messages.poll(5, TimeUnit.SECONDS);
        Assertions.assertEquals("pong", response, "Should receive pong response");

        session.close();
    }
}

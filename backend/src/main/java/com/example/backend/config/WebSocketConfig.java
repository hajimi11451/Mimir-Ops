package com.example.backend.config;

import com.example.backend.handler.ServerWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 256 * 1024;
    private static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 256 * 1024;
    private static final long MAX_SESSION_IDLE_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long ASYNC_SEND_TIMEOUT_MS = 60 * 1000L;

    @Autowired
    private ServerWebSocketHandler serverWebSocketHandler;

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
        container.setMaxSessionIdleTimeout(MAX_SESSION_IDLE_TIMEOUT_MS);
        container.setAsyncSendTimeout(ASYNC_SEND_TIMEOUT_MS);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 路径，允许跨域
        registry.addHandler(serverWebSocketHandler, "/ws/server/connect")
                .setAllowedOrigins("*");
    }
}

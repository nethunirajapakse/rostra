package com.rostra.notification.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(NotificationWebSocketHandler handler,
                           @Value("${app.ws.allowed-origins:http://localhost:3000,http://localhost:5173}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Cookies ride along on a WebSocket handshake from any site, so the Origin is checked explicitly
        // (browsers always send it; non-browser clients such as the smoke test send none and are let through).
        registry.addHandler(handler, "/ws/notifications").setAllowedOrigins(allowedOrigins);
    }
}

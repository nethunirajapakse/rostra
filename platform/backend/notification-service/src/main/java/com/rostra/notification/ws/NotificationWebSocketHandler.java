package com.rostra.notification.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live delivery of notifications. The handshake is authenticated by the normal security chain (access_token
 * cookie), so the session's principal name is the user id. Sessions are kept per user in memory: with several
 * notification-service instances you would fan out through Redis pub/sub or Kafka instead.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<UUID, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID userId = userIdOf(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("unauthenticated"));
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        sessionsByUser.computeIfAbsent(userId, id -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        log.info("WebSocket opened for user {} ({} open for this user)", userId, sessionsByUser.get(userId).size());
        safe.sendMessage(new TextMessage("{\"event\":\"connected\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userIdOf(session);
        if (userId == null) return;
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
        log.info("WebSocket closed for user {} ({})", userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket transport error: {}", exception.getMessage());
    }

    /** Runs only after the transaction that stored the notification has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(event.userId());
        if (sessions == null || sessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("event", "notification", "data", event.notification()));
        } catch (JsonProcessingException e) {
            log.error("Could not serialize notification for push: {}", e.getMessage());
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) session.sendMessage(message);
            } catch (IOException | RuntimeException e) {
                // A slow or dead socket must never affect the Kafka consumer; the REST inbox still has the row.
                log.warn("Dropping WebSocket push to a session of user {}: {}", event.userId(), e.getMessage());
            }
        }
    }

    private UUID userIdOf(WebSocketSession session) {
        try {
            return session.getPrincipal() == null ? null : UUID.fromString(session.getPrincipal().getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package com.rostra.notification.ws;

import com.rostra.notification.dto.NotificationResponse;

import java.util.UUID;

/** Published inside the consumer's transaction; pushed to the user's sockets only after it commits. */
public record NotificationCreatedEvent(UUID userId, NotificationResponse notification) {}

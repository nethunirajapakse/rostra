package com.rostra.notification.controller;

import com.rostra.notification.dto.NotificationResponse;
import com.rostra.notification.entity.Notification;
import com.rostra.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/me/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UUID userId) {
        long count = notificationRepository.countByUserIdAndReadIsFalse(userId);
        return Map.of("unreadCount", count);
    }

    /** Someone else's notification answers 404, the same as one that does not exist. */
    @PatchMapping("/{id}/read")
    @Transactional
    public NotificationResponse markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal UUID userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadIsFalse(userId);
        unread.forEach(Notification::markRead);
        return Map.of("updated", unread.size());
    }
}

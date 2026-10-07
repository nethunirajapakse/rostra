package com.rostra.notification.repository;

import com.rostra.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.rostra.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    long countByUserIdAndReadIsFalse(UUID userId);
    boolean existsBySourceEventIdAndType(UUID sourceEventId, NotificationType type);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    List<Notification> findByUserIdAndReadIsFalse(UUID userId);

    @Query("select distinct n.userId from Notification n where n.auctionId = :auctionId and n.type = :type")
    List<UUID> findUserIdsByAuctionIdAndType(@Param("auctionId") UUID auctionId, @Param("type") NotificationType type);
}

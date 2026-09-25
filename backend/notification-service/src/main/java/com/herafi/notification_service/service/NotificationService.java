package com.herafi.notification_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.notification_service.dto.CreateNotificationRequest;
import com.herafi.notification_service.dto.NotificationResponse;
import com.herafi.notification_service.entity.Notification;
import com.herafi.notification_service.exception.NotificationException;
import com.herafi.notification_service.repository.NotificationRepository;

/**
 * Stores what happened and, if the recipient is connected, tells them straight
 * away.
 *
 * Storage comes first and delivery second, on purpose: a notification nobody
 * was online to receive still has to be there on the next page load.
 */
@Service
public class NotificationService {

    static final String USER_DESTINATION = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Notification create(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setRecipientId(request.recipientId());
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setBody(request.body());
        notification.setTargetUrl(request.targetUrl());

        Notification saved = notificationRepository.save(notification);

        // Best effort: a websocket failure must not undo a stored notification,
        // which the recipient would otherwise never see at all.
        try {
            messagingTemplate.convertAndSendToUser(
                    saved.getRecipientId().toString(),
                    USER_DESTINATION,
                    NotificationResponse.from(saved));
        } catch (RuntimeException exception) {
            // Intentionally swallowed; the row is the source of truth.
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID recipientId, int page, int size) {
        return notificationRepository.findByRecipientId(
                recipientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException(HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND", "Notification not found"));

        // Someone else's notification is not even acknowledged as existing
        // beyond this: the id alone must never reveal another person's activity.
        if (!notification.getRecipientId().equals(recipientId)) {
            throw new NotificationException(HttpStatus.FORBIDDEN, "NOTIFICATION_ACCESS_DENIED",
                    "This notification belongs to someone else");
        }

        // Idempotent: reading twice keeps the first timestamp.
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return notification;
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return notificationRepository.markAllRead(recipientId, Instant.now());
    }
}

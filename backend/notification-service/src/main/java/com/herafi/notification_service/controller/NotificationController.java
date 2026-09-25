package com.herafi.notification_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.notification_service.dto.NotificationResponse;
import com.herafi.notification_service.dto.PageResponse;
import com.herafi.notification_service.dto.UnreadCountResponse;
import com.herafi.notification_service.service.NotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * What the signed-in person can see and do with their own notifications.
 * Identity always comes from X-User-Id, which only the gateway can set.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.from(
                notificationService.list(userId, page, size),
                NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@RequestHeader("X-User-Id") UUID userId) {
        return new UnreadCountResponse(notificationService.unreadCount(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId,
            @RequestHeader("X-User-Id") UUID userId) {
        return NotificationResponse.from(notificationService.markRead(notificationId, userId));
    }

    @PatchMapping("/read")
    public UnreadCountResponse markAllRead(@RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllRead(userId);
        return new UnreadCountResponse(0);
    }
}

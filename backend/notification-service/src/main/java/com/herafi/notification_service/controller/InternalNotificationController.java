package com.herafi.notification_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.notification_service.dto.CreateNotificationRequest;
import com.herafi.notification_service.dto.NotificationResponse;
import com.herafi.notification_service.service.NotificationService;

import jakarta.validation.Valid;

/**
 * How other services report that something happened. Behind the internal API
 * key: a browser must never be able to invent a notification for someone.
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return NotificationResponse.from(notificationService.create(request));
    }
}

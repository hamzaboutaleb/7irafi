package com.herafi.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.herafi.notification_service.dto.CreateNotificationRequest;
import com.herafi.notification_service.entity.Notification;
import com.herafi.notification_service.exception.NotificationException;
import com.herafi.notification_service.repository.NotificationRepository;

class NotificationServiceTests {

    private NotificationRepository notificationRepository;
    private SimpMessagingTemplate messagingTemplate;
    private NotificationService service;

    private UUID recipient;
    private UUID someoneElse;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        service = new NotificationService(notificationRepository, messagingTemplate);

        recipient = UUID.randomUUID();
        someoneElse = UUID.randomUUID();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    if (notification.getId() == null) {
                        notification.setId(UUID.randomUUID());
                        notification.setCreatedAt(Instant.now());
                    }
                    return notification;
                });
    }

    @Test
    void creatingStoresAndPushesToTheRecipient() {
        service.create(request());

        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipient.toString()), eq("/queue/notifications"), any());
    }

    @Test
    void aFailedPushStillLeavesTheNotificationStored() {
        doThrow(new RuntimeException("no broker"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        Notification saved = service.create(request());

        assertNotNull(saved.getId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markingReadSetsTheTimestamp() {
        Notification notification = stored();
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertNotNull(service.markRead(notification.getId(), recipient).getReadAt());
    }

    @Test
    void markingReadTwiceKeepsTheFirstTimestamp() {
        Notification notification = stored();
        Instant first = Instant.parse("2026-09-25T10:00:00Z");
        notification.setReadAt(first);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertEquals(first, service.markRead(notification.getId(), recipient).getReadAt());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void somebodyElsesNotificationCannotBeMarkedRead() {
        Notification notification = stored();
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        NotificationException exception = assertThrows(NotificationException.class,
                () -> service.markRead(notification.getId(), someoneElse));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertNull(notification.getReadAt());
    }

    @Test
    void anUnknownNotificationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(notificationRepository.findById(unknown)).thenReturn(Optional.empty());

        NotificationException exception = assertThrows(NotificationException.class,
                () -> service.markRead(unknown, recipient));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void markingAllReadOnlyTouchesTheCallersRows() {
        when(notificationRepository.markAllRead(eq(recipient), any())).thenReturn(3);

        assertEquals(3, service.markAllRead(recipient));
        verify(notificationRepository).markAllRead(eq(recipient), any());
    }

    @Test
    void unreadCountComesFromTheRepository() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(recipient)).thenReturn(7L);

        assertEquals(7L, service.unreadCount(recipient));
    }

    private CreateNotificationRequest request() {
        return new CreateNotificationRequest(recipient, "NEW_MESSAGE", "New message", "hello", "/conversations/1");
    }

    private Notification stored() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipient);
        notification.setType("NEW_MESSAGE");
        notification.setTitle("New message");
        notification.setBody("hello");
        notification.setCreatedAt(Instant.now());
        return notification;
    }
}

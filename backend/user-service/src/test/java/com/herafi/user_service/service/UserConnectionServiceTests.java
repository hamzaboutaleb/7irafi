package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.herafi.user_service.client.NotificationClient;
import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.UserConnection;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.ConnectionNotFoundException;
import com.herafi.user_service.exception.NotAProfessionalException;
import com.herafi.user_service.exception.SelfConnectionException;
import com.herafi.user_service.repository.ProfessionalProfileRepository;
import com.herafi.user_service.repository.UserConnectionRepository;

class UserConnectionServiceTests {

    private UserConnectionRepository userConnectionRepository;
    private ProfessionalProfileRepository professionalProfileRepository;
    private NotificationClient notificationClient;
    private UserConnectionService service;

    private UUID owner;
    private UUID target;

    @BeforeEach
    void setUp() {
        userConnectionRepository = mock(UserConnectionRepository.class);
        professionalProfileRepository = mock(ProfessionalProfileRepository.class);
        notificationClient = mock(NotificationClient.class);
        service = new UserConnectionService(
                userConnectionRepository, professionalProfileRepository, notificationClient);

        owner = UUID.randomUUID();
        target = UUID.randomUUID();
    }

    @Test
    void savesAnActiveProfessional() {
        when(professionalProfileRepository.findByUserId(target))
                .thenReturn(Optional.of(professional(target, ProfessionalOnboardingStatus.ACTIVE)));
        when(userConnectionRepository.existsByOwnerIdAndConnectionId(owner, target))
                .thenReturn(false);

        service.addConnection(owner, target);

        ArgumentCaptor<UserConnection> captor = ArgumentCaptor.forClass(UserConnection.class);
        verify(userConnectionRepository).save(captor.capture());

        UserConnection saved = captor.getValue();
        assertEquals(owner, saved.getOwnerId());
        assertEquals(target, saved.getConnectionId());
        assertTrue(saved.getCreatedAt() != null, "createdAt must be stamped");
    }

    @Test
    void rejectsSavingYourself() {
        assertThrows(SelfConnectionException.class, () -> service.addConnection(owner, owner));

        // Must fail before touching either repository — the check is first.
        verifyNoInteractions(userConnectionRepository);
        verifyNoInteractions(professionalProfileRepository);
    }

    @Test
    void rejectsSavingSomeoneWithoutAProfessionalProfile() {
        when(professionalProfileRepository.findByUserId(target)).thenReturn(Optional.empty());

        assertThrows(NotAProfessionalException.class, () -> service.addConnection(owner, target));

        verify(userConnectionRepository, never()).save(any());
    }

    @Test
    void rejectsSavingASuspendedProfessional() {
        when(professionalProfileRepository.findByUserId(target))
                .thenReturn(Optional.of(professional(target, ProfessionalOnboardingStatus.SUSPENDED)));

        assertThrows(NotAProfessionalException.class, () -> service.addConnection(owner, target));

        verify(userConnectionRepository, never()).save(any());
    }

    @Test
    void savingSomeoneAlreadySavedIsIdempotent() {
        when(professionalProfileRepository.findByUserId(target))
                .thenReturn(Optional.of(professional(target, ProfessionalOnboardingStatus.ACTIVE)));
        when(userConnectionRepository.existsByOwnerIdAndConnectionId(owner, target))
                .thenReturn(true);

        // Deliberately does not throw: the button already knows the state, so a
        // repeat request is a double-click, not a mistake.
        service.addConnection(owner, target);

        verify(userConnectionRepository, never()).save(any());
    }

    @Test
    void removesAnExistingConnection() {
        when(userConnectionRepository.deleteByOwnerIdAndConnectionId(owner, target)).thenReturn(1L);

        service.removeConnection(owner, target);

        verify(userConnectionRepository).deleteByOwnerIdAndConnectionId(owner, target);
    }

    @Test
    void removingSomethingNotInTheNetworkIs404() {
        when(userConnectionRepository.deleteByOwnerIdAndConnectionId(owner, target)).thenReturn(0L);

        assertThrows(ConnectionNotFoundException.class, () -> service.removeConnection(owner, target));
    }

    @Test
    void listsSavedProfessionals() {
        ProfessionalProfile profile = professional(target, ProfessionalOnboardingStatus.ACTIVE);

        when(userConnectionRepository.findByOwnerId(owner))
                .thenReturn(List.of(connection(owner, target)));
        when(professionalProfileRepository.findByUserIdInAndOnboardingStatus(
                Set.of(target),
                ProfessionalOnboardingStatus.ACTIVE))
                .thenReturn(List.of(profile));

        List<ProfessionalProfile> result = service.getConnections(owner);

        assertEquals(1, result.size());
        assertSame(profile, result.get(0));
    }

    @Test
    void anEmptyNetworkReturnsAnEmptyListWithoutASecondQuery() {
        when(userConnectionRepository.findByOwnerId(owner)).thenReturn(List.of());

        List<ProfessionalProfile> result = service.getConnections(owner);

        assertTrue(result.isEmpty());

        // Skipping the second call avoids issuing an IN () with no values.
        verify(professionalProfileRepository, never())
                .findByUserIdInAndOnboardingStatus(anyCollection(), any());
    }

    @Test
    void onlyActiveProfessionalsAreListed() {
        // The listing query filters on ACTIVE, so someone suspended after being
        // saved simply does not come back. The row survives, so reinstating them
        // makes them reappear.
        when(userConnectionRepository.findByOwnerId(owner))
                .thenReturn(List.of(connection(owner, target)));

        ArgumentCaptor<ProfessionalOnboardingStatus> statusCaptor =
                ArgumentCaptor.forClass(ProfessionalOnboardingStatus.class);

        when(professionalProfileRepository.findByUserIdInAndOnboardingStatus(
                anyCollection(),
                statusCaptor.capture()))
                .thenReturn(List.of());

        service.getConnections(owner);

        assertSame(ProfessionalOnboardingStatus.ACTIVE, statusCaptor.getValue());
    }

    private ProfessionalProfile professional(UUID userId, ProfessionalOnboardingStatus status) {
        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setFirstName("Amine");

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setUserProfile(userProfile);
        profile.setOnboardingStatus(status);
        return profile;
    }

    private UserConnection connection(UUID ownerId, UUID connectionId) {
        UserConnection connection = new UserConnection();
        connection.setOwnerId(ownerId);
        connection.setConnectionId(connectionId);
        return connection;
    }

    @Test
    void savingSomeoneNotifiesThem() {
        when(professionalProfileRepository.findByUserId(target)).thenReturn(Optional.of(professional(target, ProfessionalOnboardingStatus.ACTIVE)));
        when(userConnectionRepository.existsByOwnerIdAndConnectionId(owner, target)).thenReturn(false);

        service.addConnection(owner, target);

        verify(notificationClient).notify(
                eq(target), eq("NETWORK_CONNECTION_ADDED"), any(), any(), any());
    }

    @Test
    void savingSomeoneTwiceDoesNotNotifyTwice() {
        when(professionalProfileRepository.findByUserId(target)).thenReturn(Optional.of(professional(target, ProfessionalOnboardingStatus.ACTIVE)));
        when(userConnectionRepository.existsByOwnerIdAndConnectionId(owner, target)).thenReturn(true);

        service.addConnection(owner, target);

        verifyNoInteractions(notificationClient);
    }
}

package com.herafi.user_service.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.UserConnection;
import com.herafi.user_service.event.NotificationRequestedEvent;
import com.herafi.user_service.exception.ConnectionNotFoundException;
import com.herafi.user_service.exception.NotAProfessionalException;
import com.herafi.user_service.exception.SelfConnectionException;
import com.herafi.user_service.repository.ProfessionalProfileRepository;
import com.herafi.user_service.repository.UserConnectionRepository;

/**
 * A user's network: the professionals they would vouch for.
 *
 * <p>Directional by design. Saving someone does not put you in their list — the
 * reverse is a separate row that they create themselves.
 */
@Service
public class UserConnectionService {

    private final UserConnectionRepository userConnectionRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final NotificationPublisher notificationPublisher;

    public UserConnectionService(
            UserConnectionRepository userConnectionRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            NotificationPublisher notificationPublisher) {
        this.userConnectionRepository = userConnectionRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public void addConnection(UUID ownerId, UUID targetId) {
        if (ownerId.equals(targetId)) {
            throw new SelfConnectionException();
        }

        // "Professionals only" lives here rather than in the schema: professional
        // status is mutable, so a database constraint would either block valid
        // rows or go stale when someone is suspended.
        professionalProfileRepository.findByUserId(targetId)
                .filter(profile -> profile.getOnboardingStatus() == ProfessionalOnboardingStatus.ACTIVE)
                .orElseThrow(() -> new NotAProfessionalException(targetId));

        // Idempotent on purpose. The button already knows whether someone is
        // saved, so a repeat request is a double-click or a stale page, not a
        // mistake worth failing. The user wanted them saved; they are saved.
        if (userConnectionRepository.existsByOwnerIdAndConnectionId(ownerId, targetId)) {
            return;
        }

        UserConnection connection = new UserConnection();
        connection.setOwnerId(ownerId);
        connection.setConnectionId(targetId);
        connection.setCreatedAt(Instant.now());

        userConnectionRepository.save(connection);

        // Only on a genuinely new row: a double-click returns above, so nobody
        // gets told twice that the same person saved them.
        var notification = new NotificationRequestedEvent(
                UUID.randomUUID(), targetId,
                "NETWORK_CONNECTION_ADDED",
                "Someone saved your profile",
                "A client added you to their network of professionals.",
                "/professionals");
        afterCommit(() -> notificationPublisher.publish(notification));
    }

    @Transactional
    public void removeConnection(UUID ownerId, UUID targetId) {
        // The derived delete reports how many rows it removed, so this needs no
        // separate existence check. Zero means the caller named something that
        // is not in their network, which is a genuine 404.
        long removed = userConnectionRepository.deleteByOwnerIdAndConnectionId(ownerId, targetId);

        if (removed == 0) {
            throw new ConnectionNotFoundException(targetId);
        }
    }

    @Transactional(readOnly = true)
    public List<ProfessionalProfile> getConnections(UUID ownerId) {
        Set<UUID> savedIds = userConnectionRepository.findByOwnerId(ownerId).stream()
                .map(UserConnection::getConnectionId)
                .collect(Collectors.toSet());

        if (savedIds.isEmpty()) {
            // An empty network is not an error, and skipping the second query
            // avoids an IN () with no values.
            return List.of();
        }

        // Filtered to ACTIVE: someone saved months ago may since have been
        // suspended, and a suspended professional should not appear as a
        // recommendation. The row stays — only the listing hides them.
        return professionalProfileRepository.findByUserIdInAndOnboardingStatus(
                savedIds,
                ProfessionalOnboardingStatus.ACTIVE);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

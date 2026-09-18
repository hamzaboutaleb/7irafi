package com.herafi.job_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.herafi.job_service.config.JobImageProperties;
import com.herafi.job_service.entity.JobImageCleanup;
import com.herafi.job_service.repository.JobImageCleanupRepository;
import com.herafi.job_service.repository.JobImageRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JobImageCleanupService {

    private final JobImageCleanupRepository cleanupRepository;
    private final JobImageRepository imageRepository;
    private final ImageStorageService storage;
    private final JobImageProperties properties;
    private final TransactionTemplate independentTransaction;

    public JobImageCleanupService(JobImageCleanupRepository cleanupRepository,
            JobImageRepository imageRepository, ImageStorageService storage,
            JobImageProperties properties, PlatformTransactionManager transactionManager) {
        this.cleanupRepository = cleanupRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.properties = properties;
        independentTransaction = new TransactionTemplate(transactionManager);
        independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID trackUpload(String objectKey) {
        JobImageCleanup cleanup = newCleanup(objectKey);
        // Commit the recovery record before making any external storage change.
        independentTransaction.executeWithoutResult(status -> cleanupRepository.saveAndFlush(cleanup));

        // Hold this row until upload + metadata commit/rollback. Workers skip active uploads.
        cleanupRepository.findByIdForCleanup(cleanup.getId())
                .orElseThrow(() -> new IllegalStateException("Upload cleanup record is unavailable"));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    tryCleanup(cleanup.getId());
                }
            }
        });
        return cleanup.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeUpload(UUID cleanupId) {
        // This deletion commits atomically with the new image metadata.
        cleanupRepository.deleteById(cleanupId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void trackDeletion(String objectKey) {
        JobImageCleanup cleanup = cleanupRepository.save(newCleanup(objectKey));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryCleanup(cleanup.getId());
            }
        });
    }

    @Scheduled(fixedDelayString = "${jobs.images.cleanup-delay-ms:60000}",
            initialDelayString = "${jobs.images.cleanup-delay-ms:60000}")
    public void retryPending() {
        for (int attempt = 0; attempt < properties.getCleanupBatchSize(); attempt++) {
            try {
                Boolean found = independentTransaction.execute(status -> {
                    var next = cleanupRepository.findNextForCleanup(Instant.now());
                    next.ifPresent(this::deleteObject);
                    return next.isPresent();
                });
                if (!Boolean.TRUE.equals(found)) {
                    return;
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to process image cleanup queue; records remain for retry", exception);
                return;
            }
        }
    }

    private JobImageCleanup newCleanup(String objectKey) {
        JobImageCleanup cleanup = new JobImageCleanup();
        cleanup.setId(UUID.randomUUID());
        cleanup.setObjectKey(objectKey);
        cleanup.setNextAttemptAt(nextAttempt());
        return cleanup;
    }

    private Instant nextAttempt() {
        return Instant.now().plusMillis(Math.max(1, properties.getCleanupDelayMs()));
    }

    private void tryCleanup(UUID cleanupId) {
        try {
            // Transaction callbacks must not reuse the already completed transaction.
            independentTransaction.executeWithoutResult(status ->
                    cleanupRepository.findByIdForCleanup(cleanupId).ifPresent(this::deleteObject));
        } catch (RuntimeException exception) {
            log.warn("Unable to finish image cleanup {}; record remains for retry", cleanupId, exception);
        }
    }

    private void deleteObject(JobImageCleanup cleanup) {
        // An uncertain commit outcome must never delete an image with committed metadata.
        if (imageRepository.existsByObjectKey(cleanup.getObjectKey())) {
            cleanupRepository.delete(cleanup);
            return;
        }
        try {
            storage.delete(cleanup.getObjectKey());
        } catch (RuntimeException exception) {
            cleanup.setNextAttemptAt(nextAttempt());
            log.warn("Unable to delete image object {}; queued for retry", cleanup.getObjectKey(), exception);
            return;
        }
        // S3 deletion is idempotent, so a failed DB commit can safely retry the same object.
        cleanupRepository.delete(cleanup);
    }
}

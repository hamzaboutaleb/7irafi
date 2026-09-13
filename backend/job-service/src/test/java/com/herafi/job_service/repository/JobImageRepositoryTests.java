package com.herafi.job_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobImage;

@DataJpaTest
@ActiveProfiles("test")
class JobImageRepositoryTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JobImageRepository repository;

    @Test
    void selectsOnlyFirstImageForEachRequestedJobWithStableTieBreaking() {
        Job first = persistJob();
        Job second = persistJob();
        Job withoutImage = persistJob();
        Job outsidePage = persistJob();
        Instant createdAt = Instant.parse("2026-09-01T10:00:00Z");
        persistImage(first, 3, createdAt.plusSeconds(1), "later");
        persistImage(first, 2, createdAt, "tied-later-id");
        persistImage(first, 1, createdAt, "first");
        persistImage(second, 4, createdAt, "second");
        persistImage(outsidePage, 5, createdAt, "outside-page");
        entityManager.flush();
        entityManager.clear();

        var thumbnails = repository.findThumbnailsByJobIds(
                List.of(first.getId(), second.getId(), withoutImage.getId()));

        assertEquals(2, thumbnails.size());
        var keysByJob = thumbnails.stream().collect(Collectors.toMap(
                JobImageRepository.Thumbnail::getJobId,
                JobImageRepository.Thumbnail::getObjectKey));
        assertEquals(java.util.Map.of(first.getId(), "first", second.getId(), "second"), keysByJob);
    }

    private Job persistJob() {
        Job job = new Job();
        job.setClientId(UUID.randomUUID());
        job.setTitle("Fix sink");
        job.setDescription("Kitchen sink leak");
        job.setSkill("PLUMBING");
        job.setCity("CASABLANCA");
        job.setBudget(BigDecimal.valueOf(300));
        job.setEstimatedDuration(EstimatedDuration.HALF_DAY);
        entityManager.persist(job);
        return job;
    }

    private void persistImage(Job job, long id, Instant createdAt, String objectKey) {
        JobImage image = new JobImage();
        image.setId(new UUID(0, id));
        image.setJob(job);
        image.setCreatedAt(createdAt);
        image.setObjectKey(objectKey);
        entityManager.persist(image);
    }
}

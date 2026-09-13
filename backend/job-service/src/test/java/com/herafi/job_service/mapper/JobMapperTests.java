package com.herafi.job_service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.herafi.job_service.dto.JobListResponse;
import com.herafi.job_service.dto.JobDetailResponse;
import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.PreferredTime;
import com.herafi.job_service.entity.JobImage;
import com.herafi.job_service.service.ImageStorageService;

class JobMapperTests {

    private final ImageStorageService imageStorageService = Mockito.mock(ImageStorageService.class);
    private final JobMapper mapper = new JobMapper(imageStorageService);

    @Test
    void listResponseOmitsAddress() {
        JobListResponse response = mapper.toListResponse(job(JobStatus.OPEN, null), null);

        assertEquals("CASABLANCA", response.city());
        assertNull(response.thumbnailUrl());
        assertFalse(Arrays.stream(JobListResponse.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("addressLine")));
    }

    @Test
    void listResponseIncludesThumbnailUrl() {
        JobListResponse response = mapper.toListResponse(
                job(JobStatus.OPEN, null), "https://storage.example/thumbnail");

        assertEquals("https://storage.example/thumbnail", response.thumbnailUrl());
    }

    @Test
    void ownerSeesAddressInDetailResponse() {
        Job job = job(JobStatus.OPEN, null);

        JobDetailResponse response = mapper.toDetailResponse(job, job.getClientId(), job.getImages());

        assertEquals("123 Main Street", response.addressLine());
    }

    @Test
    void assignedWorkerSeesAddressInDetailResponse() {
        UUID workerId = UUID.randomUUID();
        Job job = job(JobStatus.ASSIGNED, workerId);

        JobDetailResponse response = mapper.toDetailResponse(job, workerId, job.getImages());

        assertEquals("123 Main Street", response.addressLine());
    }

    @Test
    void unrelatedUserDoesNotSeeAddressInDetailResponse() {
        Job job = job(JobStatus.OPEN, null);

        JobDetailResponse response = mapper.toDetailResponse(job, UUID.randomUUID(), job.getImages());

        assertNull(response.addressLine());
    }

    @Test
    void detailResponseIncludesFreshImageUrls() {
        Job job = job(JobStatus.OPEN, null);
        JobImage image = new JobImage();
        image.setId(UUID.randomUUID());
        image.setObjectKey("jobs/" + job.getId() + "/" + image.getId());
        image.setJob(job);
        job.setImages(List.of(image));
        Mockito.when(imageStorageService.generateDownloadUrl(image.getObjectKey()))
                .thenReturn("http://localhost:9000/presigned-image");

        JobDetailResponse response = mapper.toDetailResponse(job, job.getClientId(), job.getImages());

        assertEquals(1, response.images().size());
        assertEquals(image.getId(), response.images().get(0).id());
        assertEquals("http://localhost:9000/presigned-image", response.images().get(0).url());
    }

    private Job job(JobStatus status, UUID assignedWorkerId) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setClientId(UUID.randomUUID());
        job.setTitle("Fix sink");
        job.setDescription("Kitchen sink leak");
        job.setSkill("PLUMBING");
        job.setCity("CASABLANCA");
        job.setAddressLine("123 Main Street");
        job.setBudget(BigDecimal.valueOf(300));
        job.setEstimatedDuration(EstimatedDuration.HALF_DAY);
        job.setPreferredTime(PreferredTime.MORNING);
        job.setStatus(status);
        job.setAssignedWorkerId(assignedWorkerId);
        job.setCreatedAt(Instant.parse("2026-09-03T10:00:00Z"));
        job.setUpdatedAt(Instant.parse("2026-09-03T10:00:00Z"));
        return job;
    }
}

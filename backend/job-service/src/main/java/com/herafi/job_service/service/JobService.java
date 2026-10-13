package com.herafi.job_service.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.herafi.job_service.config.JobImageProperties;
import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.JobDetailResponse;
import com.herafi.job_service.dto.JobFilter;
import com.herafi.job_service.dto.JobImageResponse;
import com.herafi.job_service.dto.JobListResponse;
import com.herafi.job_service.dto.JobSummaryResponse;
import com.herafi.job_service.dto.PageResponse;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.City;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobImage;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.Skill;
import com.herafi.job_service.exceptions.InvalidImageException;
import com.herafi.job_service.exceptions.InvalidJobStateException;
import com.herafi.job_service.exceptions.InvalidReferenceException;
import com.herafi.job_service.exceptions.InvalidSortException;
import com.herafi.job_service.exceptions.JobAccessDeniedException;
import com.herafi.job_service.exceptions.JobNotFoundException;
import com.herafi.job_service.exceptions.JobNotOpenException;
import com.herafi.job_service.exceptions.JobStateConflictException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.repository.CityRepository;
import com.herafi.job_service.repository.JobImageRepository;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.repository.SkillRepository;
import com.herafi.job_service.specification.JobSpecification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "budget", "title");

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final JobImageRepository jobImageRepository;
    private final ImageStorageService imageStorageService;
    private final SkillRepository skillRepository;
    private final CityRepository cityRepository;
    private final JobImageProperties imageProperties;
    private final MeterRegistry meterRegistry;

    public Job create(UUID clientId, CreateJobRequest request) {
        validateReferences(request);

        Job job = new Job();

        job.setClientId(clientId);
        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setSkill(request.skill());
        job.setCity(request.city());
        job.setAddressLine(request.addressLine());
        job.setBudget(request.budget());
        job.setEstimatedDuration(request.estimatedDuration());

        if (request.preferredTime() != null) {
            job.setPreferredTime(request.preferredTime());
        }

        Job savedJob = jobRepository.save(job);

        Counter.builder("herafi.jobs.posted")
                .description("Jobs successfully created")
                .register(meterRegistry)
                .increment();

        return savedJob;
    }

    private void validateReferences(CreateJobRequest request) {
        if (!skillRepository.existsByCodeAndActiveTrue(request.skill())) {
            throw new InvalidReferenceException("Unknown or inactive skill: " + request.skill());
        }

        if (!cityRepository.existsByCodeAndActiveTrue(request.city())) {
            throw new InvalidReferenceException("Unknown or inactive city: " + request.city());
        }
    }

    public PageResponse<JobListResponse> getJobs(
            JobFilter filter,
            String sort,
            int page,
            int size) {

        Specification<Job> specification = JobSpecification.filter(filter);

        if (sort == null || sort.isBlank()) {
            throw new InvalidSortException("Sort must be field or field,asc|desc");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidSortException("Sort must be field or field,asc|desc");
        }

        String field = parts[0].trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new InvalidSortException(
                    "Invalid sort field: " + field);
        }

        Sort.Direction direction;

        if (parts.length > 1) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException exception) {
                throw new InvalidSortException(
                        "Invalid sort direction: " + parts[1]);
            }
        } else {
            direction = Sort.Direction.ASC;
        }

        Sort sorting = Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));

        Pageable pageable = PageRequest.of(
                page,
                size,
                sorting);

        Page<Job> jobs = jobRepository.findAll(specification, pageable);

        Map<UUID, String> thumbnailUrls = new HashMap<>();
        if (jobs.hasContent()) {
            List<UUID> jobIds = jobs.getContent().stream().map(Job::getId).toList();
            for (JobImageRepository.Thumbnail thumbnail : jobImageRepository.findThumbnailsByJobIds(jobIds)) {
                try {
                    thumbnailUrls.put(thumbnail.getJobId(),
                            imageStorageService.generateDownloadUrl(thumbnail.getObjectKey()));
                } catch (RuntimeException exception) {
                    log.warn("Unable to sign thumbnail for job {}", thumbnail.getJobId(), exception);
                }
            }
        }

        return PageResponse.from(
                jobs,
                job -> jobMapper.toListResponse(job, thumbnailUrls.get(job.getId())));
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJobDetail(UUID id, UUID viewerId) {
        Job job = jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", id));
        List<JobImage> images = jobImageRepository.findByJob_IdOrderByCreatedAtAscIdAsc(id);

        return jobMapper.toDetailResponse(job, viewerId, images);
    }

    public List<Job> getMyJobs(UUID clientId) {
        return jobRepository.findByClientIdAndDeletedFalse(clientId);
    }

    @Transactional
    public Job update(UUID id, UUID clientId, UpdateJobRequest request) {

        Job job = jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

        if (!job.getClientId().equals(clientId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to update this job");
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new JobStateConflictException(
                    "Jobs that not Open cannot be updated");
        }

        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setAddressLine(request.addressLine());

        return job;
    }

    @Transactional
    public Job complete(UUID id, UUID userId) {

        Job job = jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

        if (!job.getClientId().equals(userId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to complete this job");
        }

        if (job.getStatus() != JobStatus.ASSIGNED) {
            throw new InvalidJobStateException(job.getStatus());
        }

        job.setStatus(JobStatus.COMPLETED);

        return job;
    }

    public List<String> getAllSkills() {
        return skillRepository.findByActiveTrueOrderByCodeAsc()
                .stream()
                .map(Skill::getCode)
                .toList();
    }

    public List<String> getAllCities() {
        return cityRepository.findByActiveTrueOrderByCodeAsc()
                .stream()
                .map(City::getCode)
                .toList();
    }

    @Transactional
    public Job cancelJob(UUID jobId, UUID userId, boolean isAdmin) {

        Job job = jobRepository.findByIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", jobId));

        if (!isAdmin && !job.getClientId().equals(userId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to cancel this job");
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new JobStateConflictException(
                    "Only open jobs can be cancelled");
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setAssignedWorkerId(null);

        return job;
    }

    @Transactional
    public void deleteJob(UUID jobId, UUID userId, boolean isAdmin) {

        Job job = jobRepository.findByIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", jobId));

        // Authorization: only ADMIN or job owner
        if (!isAdmin && !job.getClientId().equals(userId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to delete this job");
        }

        if (job.getStatus() == JobStatus.ASSIGNED) {
            throw new JobStateConflictException(
                    "Assigned jobs cannot be deleted");
        }

        job.setDeleted(true);
        job.setDeletedAt(Instant.now());
        job.setDeletedBy(userId);
        afterCommit(() -> jobImageRepository.findObjectKeysByJobId(jobId)
                .forEach(this::markPendingCleanup));
    }

    public JobSummaryResponse getSummary(UUID id) {
        Job job = jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

        return new JobSummaryResponse(
                job.getId(),
                job.getClientId(),
                job.getStatus(), job.getAssignedWorkerId());
    }

    @Transactional
    public JobSummaryResponse assignWorker(UUID id, UUID workerId) {
        Job job = jobRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

        if (job.getStatus() == JobStatus.ASSIGNED && workerId.equals(job.getAssignedWorkerId())) {
            return new JobSummaryResponse(
                    job.getId(),
                    job.getClientId(),
                    job.getStatus(), job.getAssignedWorkerId());
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new JobNotOpenException();
        }

        job.setStatus(JobStatus.ASSIGNED);
        job.setAssignedWorkerId(workerId);

        jobRepository.save(job);

        return new JobSummaryResponse(
                job.getId(),
                job.getClientId(),
                job.getStatus(), job.getAssignedWorkerId());
    }

    @Transactional
    public JobImageResponse uploadImage(
            UUID jobId,
            UUID userId,
            MultipartFile file) {

        if (file.isEmpty()) {
            throw new InvalidImageException("Image cannot be empty");
        }

        if (file.getSize() > imageProperties.getMaxFileSize().toBytes()) {
            throw new InvalidImageException(
                    "Image size must not exceed " + imageProperties.maxFileSizeLabel());
        }

        Job job = jobRepository.findByIdAndDeletedFalseForUpdate(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", jobId));

        if (!job.getClientId().equals(userId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to update this job");
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new JobStateConflictException("Only open jobs can receive images");
        }

        if (jobImageRepository.countByJob_Id(jobId) >= imageProperties.getMaxCount()) {
            throw new InvalidImageException(
                    "Maximum image count is " + imageProperties.getMaxCount());
        }

        String contentType = detectImageContentType(file);

        UUID imageId = UUID.randomUUID();
        String objectKey = "jobs/" + jobId + "/" + imageId;
        try (InputStream inputStream = file.getInputStream()) {
            imageStorageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    contentType);
        } catch (IOException e) {
            Counter.builder("herafi.job.image.upload.failures")
                    .description("Job image upload failures")
                    .register(meterRegistry)
                    .increment();
            throw new InvalidImageException("Failed to read image");
        }

        JobImage image = new JobImage();
        image.setId(imageId);
        image.setJob(job);
        image.setObjectKey(objectKey);
        image.setCreatedAt(Instant.now());

        jobImageRepository.saveAndFlush(image);

        String url = imageStorageService.generateDownloadUrl(objectKey);
        afterCommit(() -> markCommitted(objectKey));

        return new JobImageResponse(
                imageId,
                url);
    }

    @Transactional
    public void deleteImage(UUID jobId, UUID imageId, UUID userId) {
        Job job = jobRepository.findByIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: ", jobId));

        if (!job.getClientId().equals(userId)) {
            throw new JobAccessDeniedException(
                    "You are not allowed to manage images for this job");
        }

        JobImage image = jobImageRepository.findById(imageId)
                .filter(candidate -> candidate.getJob().getId().equals(jobId))
                .orElseThrow(() -> new JobNotFoundException("Image not found: ", imageId));

        jobImageRepository.delete(image);
        afterCommit(() -> markPendingCleanup(image.getObjectKey()));
    }

    private void markCommitted(String objectKey) {
        try {
            imageStorageService.markCommitted(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to mark uploaded image as committed: {}", objectKey, exception);
        }
    }

    private void markPendingCleanup(String objectKey) {
        try {
            imageStorageService.markPendingCleanup(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Failed to mark image for lifecycle cleanup: {}", objectKey, exception);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String detectImageContentType(MultipartFile file) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        } catch (IOException exception) {
            throw new InvalidImageException("Failed to read image");
        }

        if (isJpeg(header)) {
            return "image/jpeg";
        }

        if (isPng(header)) {
            return "image/png";
        }

        if (isWebp(header)) {
            return "image/webp";
        }

        throw new InvalidImageException("Unsupported image type");
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return header.length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }
}

package com.herafi.job_service.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.herafi.job_service.dto.ChatEligibilityResponse;
import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.JobFilter;
import com.herafi.job_service.dto.JobImageResponse;
import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.dto.JobSummaryResponse;
import com.herafi.job_service.dto.PageResponse;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobImage;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.exceptions.InvalidImageException;
import com.herafi.job_service.exceptions.InvalidJobStateException;
import com.herafi.job_service.exceptions.InvalidSortException;
import com.herafi.job_service.exceptions.JobAccessDeniedException;
import com.herafi.job_service.exceptions.JobNotFoundException;
import com.herafi.job_service.exceptions.JobStateConflictException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.repository.JobImageRepository;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.specification.JobSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobService {

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "budget", "title");

	private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/gif",
			"image/bmp", "image/tiff", "image/svg+xml", "image/x-icon", "image/heic", "image/heif");

	private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
	private final JobRepository jobRepository;
	private final JobMapper jobMapper;
	private final JobImageRepository jobImageRepository;
	private final ImageStorageService imageStorageService;

	public Job create(UUID clientId, CreateJobRequest request) {
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

		return jobRepository.save(job);
	}

	public PageResponse<JobResponse> getJobs(
			JobFilter filter,
			String sort,
			int page,
			int size) {

		Specification<Job> specification = JobSpecification.filter(filter);

		String[] parts = sort.split(",");

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

		Sort sorting = Sort.by(direction, field);

		Pageable pageable = PageRequest.of(
				page,
				size,
				sorting);

		Page<Job> jobs = jobRepository.findAll(specification, pageable);

		return PageResponse.from(
				jobs,
				jobMapper::toResponse);
	}

	public Job getJob(UUID id) {
		return jobRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", id));
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
		job.setAddressLine(request.location());

		return job;
	}

	@Transactional
	public Job complete(UUID id) {

		Job job = jobRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

		if (job.getStatus() != JobStatus.ASSIGNED) {
			throw new InvalidJobStateException(job.getStatus());
		}

		job.setStatus(JobStatus.COMPLETED);

		return job;
	}

	public List<String> getAllSkills() {
		return jobRepository.findAllSkills();
	}

	public List<String> getAllCities() {
		return jobRepository.findAllCities();
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

		// assigned jobs must be cancelled instead
		if (job.getStatus() == JobStatus.ASSIGNED) {
			throw new JobStateConflictException(
					"Assigned jobs cannot be deleted. Cancel the job instead.");
		}

		job.setDeleted(true);
		job.setDeletedAt(Instant.now());
		job.setDeletedBy(userId);
	}

	public JobSummaryResponse getSummary(UUID id) {
		Job job = jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

		return new JobSummaryResponse(
				job.getId(),
				job.getClientId(),
				job.getStatus());
	}

	public JobSummaryResponse assignWorker(UUID id, UUID workerId) {
		Job job = jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

		if (job.getStatus() != JobStatus.OPEN) {
			throw new JobStateConflictException(
					"Job must be OPEN to be assigned.");
		}

		job.setStatus(JobStatus.ASSIGNED);
		job.setAssignedWorkerId(workerId);

		jobRepository.save(job);

		return new JobSummaryResponse(
				job.getId(),
				job.getClientId(),
				job.getStatus());
	}

	public ChatEligibilityResponse getChatEligibility(UUID id) {
		Job job = jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", id));

		boolean allowed = job.getStatus() == JobStatus.ASSIGNED ||
				job.getStatus() == JobStatus.COMPLETED;

		return new ChatEligibilityResponse(
				allowed,
				job.getClientId());
	}

	public JobImageResponse uploadImage(
			UUID jobId,
			UUID userId,
			MultipartFile file) throws IOException {

		if (file.isEmpty()) {
			throw new InvalidImageException("Image cannot be empty");
		}

		if (file.getSize() > MAX_IMAGE_SIZE) {
		throw new InvalidImageException("Image size must not exceed 5 MB");
		}

		Job job = jobRepository.findById(jobId)
				.orElseThrow(() -> new JobNotFoundException("Job not found: ", jobId));

		if (!job.getClientId().equals(userId)) {
			throw new JobAccessDeniedException(
					"You are not allowed to update this job");
		}

		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
			throw new InvalidImageException("Unsupported image type");
		}

		UUID imageId = UUID.randomUUID();
		String objectKey = "jobs/" + jobId + "/" + imageId;
		try {
			imageStorageService.upload(
					objectKey,
					file.getInputStream(),
					file.getSize(),
					file.getContentType());
		} catch (IOException e) {
			throw new InvalidImageException("Failed to read image");
		}

		JobImage image = new JobImage();
		image.setId(imageId);
		image.setJob(job);
		image.setObjectKey(objectKey);
		image.setPosition(job.getImages().size());
		image.setCreatedAt(Instant.now());

		jobImageRepository.save(image);

		String url = imageStorageService.generateDownloadUrl(objectKey);

		return new JobImageResponse(
				imageId,
				url,
				image.getPosition());
	}
}
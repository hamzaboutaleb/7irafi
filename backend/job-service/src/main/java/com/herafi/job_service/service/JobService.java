package com.herafi.job_service.service;

import java.util.UUID;
import java.nio.file.AccessDeniedException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.exceptions.InvalidJobStateException;
import com.herafi.job_service.exceptions.JobAccessDeniedException;
import com.herafi.job_service.exceptions.JobNotFoundException;
import com.herafi.job_service.exceptions.JobStateConflictException;
import com.herafi.job_service.exceptions.JobAccessDeniedException;
import com.herafi.job_service.repository.JobRepository;

@Service
public class JobService {

	private final JobRepository jobRepository;

	public JobService(JobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

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

	public List<Job> getJobs() {
		return jobRepository.findAll();
	}

	public Job getJob(UUID id) {
		return jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("Jobs not found"));
	}

	public List<Job> getMyJobs(UUID clientId) {
		return jobRepository.findByClientId(clientId);
	}

	public Job update(UUID id, UUID clientId, UpdateJobRequest request) {

		Job job = jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("job not found: ", id));

		// User is not the owner
		if (!job.getClientId().equals(clientId)) {
			throw new JobAccessDeniedException();
		}

		// Job cannot be modified unless OPEN
		if (job.getStatus() != JobStatus.OPEN) {
			throw new JobStateConflictException();
		}

		job.setTitle(request.title());
		job.setDescription(request.description());
		job.setAddressLine(request.location());

		return jobRepository.save(job);
	}

	public Job complete(UUID id) {

		Job job = jobRepository.findById(id)
				.orElseThrow(() -> new JobNotFoundException("job not found: ", id));

		if (job.getStatus() != JobStatus.ASSIGNED) {
			throw new InvalidJobStateException(job.getStatus());
		}

		job.setStatus(JobStatus.COMPLETED);

		return jobRepository.save(job);
	}

	public List<String> getAllSkills() {
		return jobRepository.findAllSkills();
	}

	public List<String> getAllCities() {
		return jobRepository.findAllCities();
	}
}

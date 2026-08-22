package com.herafi.job_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.entity.Job;
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
}

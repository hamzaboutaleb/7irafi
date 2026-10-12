package com.herafi.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.herafi.job_service.config.JobImageProperties;
import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.exceptions.InvalidImageException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.repository.CityRepository;
import com.herafi.job_service.repository.JobImageRepository;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.repository.SkillRepository;

class JobServiceMetricsTests {

    @Test
    void createIncrementsTheJobsCreatedCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobRepository jobRepository = mock(JobRepository.class);
        SkillRepository skillRepository = mock(SkillRepository.class);
        CityRepository cityRepository = mock(CityRepository.class);

        JobService service = new JobService(
                jobRepository,
                mock(JobMapper.class),
                mock(JobImageRepository.class),
                mock(ImageStorageService.class),
                skillRepository,
                cityRepository,
                new JobImageProperties(),
                registry);

        when(skillRepository.existsByCodeAndActiveTrue("PLUMBING")).thenReturn(true);
        when(cityRepository.existsByCodeAndActiveTrue("CASABLANCA")).thenReturn(true);
        when(jobRepository.save(any(Job.class))).thenReturn(new Job());

        CreateJobRequest request = new CreateJobRequest(
                "Fix the sink", "Kitchen sink is leaking", "PLUMBING", "CASABLANCA",
                null, BigDecimal.ONE, EstimatedDuration.HALF_DAY, null);

        service.create(UUID.randomUUID(), request);

        assertThat(registry.counter("herafi.jobs.created").count()).isEqualTo(1.0);
    }

    @Test
    void uploadFailureIncrementsTheImageUploadFailuresCounter() throws IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobRepository jobRepository = mock(JobRepository.class);
        JobImageRepository jobImageRepository = mock(JobImageRepository.class);

        JobService service = new JobService(
                jobRepository,
                mock(JobMapper.class),
                jobImageRepository,
                mock(ImageStorageService.class),
                mock(SkillRepository.class),
                mock(CityRepository.class),
                new JobImageProperties(),
                registry);

        UUID clientId = UUID.randomUUID();

        Job job = new Job();
        job.setClientId(clientId);
        job.setStatus(JobStatus.OPEN);

        when(jobRepository.findByIdAndDeletedFalseForUpdate(any())).thenReturn(Optional.of(job));
        when(jobImageRepository.countByJob_Id(any())).thenReturn(0L);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);

        // First call feeds detectImageContentType a valid JPEG header; the second
        // call (the upload itself) fails to open the stream.
        byte[] jpegHeader = new byte[12];
        jpegHeader[0] = (byte) 0xFF;
        jpegHeader[1] = (byte) 0xD8;
        jpegHeader[2] = (byte) 0xFF;
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream(jpegHeader))
                .thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> service.uploadImage(UUID.randomUUID(), clientId, file))
                .isInstanceOf(InvalidImageException.class);

        assertThat(registry.counter("herafi.job.image.upload.failures").count()).isEqualTo(1.0);
    }
}

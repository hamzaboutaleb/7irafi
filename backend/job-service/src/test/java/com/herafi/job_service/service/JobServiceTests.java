package com.herafi.job_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.City;
import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobImage;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.PreferredTime;
import com.herafi.job_service.entity.Skill;
import com.herafi.job_service.exceptions.InvalidImageException;
import com.herafi.job_service.exceptions.InvalidJobStateException;
import com.herafi.job_service.exceptions.InvalidReferenceException;
import com.herafi.job_service.exceptions.JobAccessDeniedException;
import com.herafi.job_service.exceptions.JobNotFoundException;
import com.herafi.job_service.exceptions.JobNotOpenException;
import com.herafi.job_service.exceptions.JobStateConflictException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.repository.CityRepository;
import com.herafi.job_service.repository.JobImageRepository;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.repository.SkillRepository;

@ExtendWith(MockitoExtension.class)
class JobServiceTests {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };
    private static final byte[] WEBP_SIGNATURE = {
            0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50
    };

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobImageRepository jobImageRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private JobService service;

    @Test
    void createsJobWhenReferencesAreActive() {
        UUID clientId = UUID.randomUUID();
        CreateJobRequest request = createRequest("PLUMBING", "RABAT");
        when(skillRepository.existsByCodeAndActiveTrue("PLUMBING")).thenReturn(true);
        when(cityRepository.existsByCodeAndActiveTrue("RABAT")).thenReturn(true);
        when(jobRepository.save(org.mockito.ArgumentMatchers.any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Job result = service.create(clientId, request);

        assertEquals(clientId, result.getClientId());
        assertEquals("PLUMBING", result.getSkill());
        assertEquals("RABAT", result.getCity());
        verify(jobRepository).save(result);
    }

    @Test
    void rejectsCreateWithUnknownOrInactiveSkill() {
        CreateJobRequest request = createRequest("UNKNOWN", "RABAT");
        when(skillRepository.existsByCodeAndActiveTrue("UNKNOWN")).thenReturn(false);

        assertThrows(
                InvalidReferenceException.class,
                () -> service.create(UUID.randomUUID(), request));

        verifyNoMoreInteractions(cityRepository, jobRepository);
    }

    @Test
    void rejectsCreateWithUnknownOrInactiveCity() {
        CreateJobRequest request = createRequest("PLUMBING", "UNKNOWN");
        when(skillRepository.existsByCodeAndActiveTrue("PLUMBING")).thenReturn(true);
        when(cityRepository.existsByCodeAndActiveTrue("UNKNOWN")).thenReturn(false);

        assertThrows(
                InvalidReferenceException.class,
                () -> service.create(UUID.randomUUID(), request));

        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void acceptsImageJustUnderConfiguredLimit() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes(MAX_IMAGE_BYTES - 1, PNG_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        service.uploadImage(jobId, ownerId, file);

        verify(imageStorageService).upload(
                ArgumentMatchers.startsWith("jobs/" + jobId + "/"),
                ArgumentMatchers.any(InputStream.class),
                ArgumentMatchers.eq((long) (MAX_IMAGE_BYTES - 1)),
                ArgumentMatchers.eq("image/png"));
        verify(jobImageRepository).saveAndFlush(ArgumentMatchers.any());
    }

    @Test
    void acceptsValidJpegSignature() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "application/octet-stream",
                imageBytes(64, JPEG_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        service.uploadImage(jobId, ownerId, file);

        verify(imageStorageService).upload(
                ArgumentMatchers.startsWith("jobs/" + jobId + "/"),
                ArgumentMatchers.any(InputStream.class),
                ArgumentMatchers.eq(64L),
                ArgumentMatchers.eq("image/jpeg"));
    }

    @Test
    void acceptsValidWebpSignature() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.webp",
                "application/octet-stream",
                imageBytes(64, WEBP_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        service.uploadImage(jobId, ownerId, file);

        verify(imageStorageService).upload(
                ArgumentMatchers.startsWith("jobs/" + jobId + "/"),
                ArgumentMatchers.any(InputStream.class),
                ArgumentMatchers.eq(64L),
                ArgumentMatchers.eq("image/webp"));
    }

    @Test
    void deletesUploadedObjectWhenImageMetadataSaveFails() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes(64, PNG_SIGNATURE));
        RuntimeException saveFailure = new RuntimeException("database unavailable");
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobImageRepository.saveAndFlush(ArgumentMatchers.any())).thenThrow(saveFailure);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        assertSame(saveFailure, exception);
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(imageStorageService).upload(
                objectKey.capture(),
                ArgumentMatchers.any(InputStream.class),
                ArgumentMatchers.eq(64L),
                ArgumentMatchers.eq("image/png"));
        verify(imageStorageService).delete(objectKey.getValue());
        verify(imageStorageService, never()).generateDownloadUrl(ArgumentMatchers.anyString());
    }

    @Test
    void ownerDeletesImageAndItsObject() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        JobImage image = image(imageId, job, "jobs/" + jobId + "/" + imageId);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));
        when(jobImageRepository.findById(imageId)).thenReturn(Optional.of(image));

        service.deleteImage(jobId, imageId, ownerId);

        verify(imageStorageService).delete(image.getObjectKey());
        verify(jobImageRepository).delete(image);
    }

    @Test
    void rejectsImageDeletionByNonOwner() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobAccessDeniedException.class,
                () -> service.deleteImage(jobId, UUID.randomUUID(), UUID.randomUUID()));

        verifyNoMoreInteractions(jobImageRepository, imageStorageService);
    }

    @Test
    void rejectsImageFromAnotherJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job otherJob = job(UUID.randomUUID(), ownerId, JobStatus.OPEN);
        JobImage image = image(UUID.randomUUID(), otherJob, "jobs/other/image");
        when(jobRepository.findByIdAndDeletedFalse(jobId))
                .thenReturn(Optional.of(job(jobId, ownerId, JobStatus.OPEN)));
        when(jobImageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        assertThrows(
                JobNotFoundException.class,
                () -> service.deleteImage(jobId, image.getId(), ownerId));

        verifyNoMoreInteractions(imageStorageService);
        verify(jobImageRepository).findById(image.getId());
    }

    @Test
    void rejectsNonImageClaimingJpeg() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                new byte[] { 1, 2, 3 });
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        InvalidImageException exception = assertThrows(
                InvalidImageException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        assertEquals("Unsupported image type", exception.getMessage());
        verify(jobImageRepository).countByJob_Id(jobId);
        verify(jobImageRepository, never()).saveAndFlush(ArgumentMatchers.any());
        verifyNoMoreInteractions(imageStorageService);
    }

    @Test
    void rejectsSvgUpload() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        InvalidImageException exception = assertThrows(
                InvalidImageException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        assertEquals("Unsupported image type", exception.getMessage());
        verify(jobImageRepository).countByJob_Id(jobId);
        verify(jobImageRepository, never()).saveAndFlush(ArgumentMatchers.any());
        verifyNoMoreInteractions(imageStorageService);
    }

    @Test
    void rejectsImageUploadForDeletedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes(64, PNG_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        verifyNoMoreInteractions(imageStorageService, jobImageRepository);
    }

    @Test
    void rejectsImageUploadForAssignedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes(64, PNG_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobStateConflictException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        verifyNoMoreInteractions(imageStorageService, jobImageRepository);
    }

    @Test
    void rejectsImageUploadWhenMaxCountReached() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes(64, PNG_SIGNATURE));
        when(jobRepository.findByIdAndDeletedFalseForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobImageRepository.countByJob_Id(jobId)).thenReturn(10L);

        InvalidImageException exception = assertThrows(
                InvalidImageException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        assertEquals("Maximum image count is 10", exception.getMessage());
        verifyNoMoreInteractions(imageStorageService);
    }

    @Test
    void rejectsImageJustOverConfiguredLimit() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                new byte[MAX_IMAGE_BYTES + 1]);

        InvalidImageException exception = assertThrows(
                InvalidImageException.class,
                () -> service.uploadImage(jobId, ownerId, file));

        assertEquals("Image size must not exceed 10MB", exception.getMessage());
        verifyNoMoreInteractions(jobRepository, imageStorageService, jobImageRepository);
    }

    @Test
    void ownerUpdatesOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        Job result = service.update(
                jobId,
                ownerId,
                new UpdateJobRequest("Updated title", "Updated description", "456 New Address"));

        assertSame(job, result);
        assertEquals("Updated title", job.getTitle());
        assertEquals("Updated description", job.getDescription());
        assertEquals("456 New Address", job.getAddressLine());
    }

    @Test
    void returnsActiveSkillCodesFromReferenceTable() {
        when(skillRepository.findByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(skill("ELECTRICAL"), skill("PLUMBING")));

        assertEquals(List.of("ELECTRICAL", "PLUMBING"), service.getAllSkills());
    }

    @Test
    void returnsActiveCityCodesFromReferenceTable() {
        when(cityRepository.findByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(city("CASABLANCA"), city("RABAT")));

        assertEquals(List.of("CASABLANCA", "RABAT"), service.getAllCities());
    }

    @Test
    void ownerCompletesAssignedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        Job result = service.complete(jobId, ownerId);

        assertSame(job, result);
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void rejectsCompletionByNonOwner() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobAccessDeniedException.class,
                () -> service.complete(jobId, otherUserId));

        assertEquals(JobStatus.ASSIGNED, job.getStatus());
        verify(jobRepository).findByIdAndDeletedFalse(jobId);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void rejectsCompletionWhenJobIsOpen() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                InvalidJobStateException.class,
                () -> service.complete(jobId, ownerId));

        assertEquals(JobStatus.OPEN, job.getStatus());
    }

    @Test
    void assignsOpenJobToWorker() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertEquals(JobStatus.ASSIGNED, service.assignWorker(jobId, workerId).status());

        assertEquals(JobStatus.ASSIGNED, job.getStatus());
        assertEquals(workerId, job.getAssignedWorkerId());
        verify(jobRepository).save(job);
    }

    @Test
    void rejectsAssigningNonOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobNotOpenException.class,
                () -> service.assignWorker(jobId, workerId));

        verify(jobRepository).findByIdAndDeletedFalse(jobId);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void assignmentRetryWithSameWorkerSucceeds() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        job.setAssignedWorkerId(workerId);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertEquals(JobStatus.ASSIGNED, service.assignWorker(jobId, workerId).status());

        verify(jobRepository).findByIdAndDeletedFalse(jobId);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void assignmentRetryWithDifferentWorkerFails() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID assignedWorkerId = UUID.randomUUID();
        UUID otherWorkerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        job.setAssignedWorkerId(assignedWorkerId);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobNotOpenException.class,
                () -> service.assignWorker(jobId, otherWorkerId));

        assertEquals(assignedWorkerId, job.getAssignedWorkerId());
    }

    @Test
    void assignmentFailsForCompletedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.COMPLETED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobNotOpenException.class,
                () -> service.assignWorker(jobId, workerId));
    }

    @Test
    void assignmentFailsForCancelledJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.CANCELLED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobNotOpenException.class,
                () -> service.assignWorker(jobId, workerId));
    }

    @Test
    void internalSummaryDoesNotReturnDeletedJobs() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> service.getSummary(jobId));
    }

    @Test
    void chatEligibilityDoesNotReturnDeletedJobs() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.empty());

        assertThrows(
                JobNotFoundException.class,
                () -> service.getChatEligibility(jobId));
    }

    @Test
    void chatEligibilityIsAllowedForAssignedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertEquals(true, service.getChatEligibility(jobId).allowed());
    }

    @Test
    void chatEligibilityIsDeniedForOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertEquals(false, service.getChatEligibility(jobId).allowed());
    }

    @Test
    void ownerCancelsOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        Job result = service.cancelJob(jobId, ownerId, false);

        assertSame(job, result);
        assertEquals(JobStatus.CANCELLED, job.getStatus());
    }

    @Test
    void adminCancelsNonOwnedOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        service.cancelJob(jobId, adminId, true);

        assertEquals(JobStatus.CANCELLED, job.getStatus());
    }

    @Test
    void rejectsCancelByNonOwner() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobAccessDeniedException.class,
                () -> service.cancelJob(jobId, otherUserId, false));

        assertEquals(JobStatus.OPEN, job.getStatus());
    }

    @Test
    void rejectsCancelForAssignedJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.ASSIGNED);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        assertThrows(
                JobStateConflictException.class,
                () -> service.cancelJob(jobId, ownerId, false));

        assertEquals(JobStatus.ASSIGNED, job.getStatus());
    }

    @Test
    void deleteJobStillSoftDeletesEligibleJob() {
        UUID jobId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Job job = job(jobId, ownerId, JobStatus.OPEN);
        when(jobRepository.findByIdAndDeletedFalse(jobId)).thenReturn(Optional.of(job));

        service.deleteJob(jobId, ownerId, false);

        assertEquals(true, job.isDeleted());
        assertEquals(ownerId, job.getDeletedBy());
    }

    private Job job(UUID id, UUID clientId, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setClientId(clientId);
        job.setStatus(status);
        return job;
    }

    private JobImage image(UUID id, Job job, String objectKey) {
        JobImage image = new JobImage();
        image.setId(id);
        image.setJob(job);
        image.setObjectKey(objectKey);
        return image;
    }

    private byte[] imageBytes(int size, byte[] signature) {
        byte[] bytes = new byte[size];
        System.arraycopy(signature, 0, bytes, 0, Math.min(size, signature.length));
        return bytes;
    }

    private CreateJobRequest createRequest(String skill, String city) {
        return new CreateJobRequest(
                "Fix sink",
                "Kitchen sink leaks",
                skill,
                city,
                "123 Main Street",
                BigDecimal.valueOf(500),
                EstimatedDuration.HALF_DAY,
                PreferredTime.ANYTIME);
    }

    private Skill skill(String code) {
        Skill skill = new Skill();
        skill.setCode(code);
        skill.setActive(true);
        return skill;
    }

    private City city(String code) {
        City city = new City();
        city.setCode(code);
        city.setActive(true);
        return city;
    }
}

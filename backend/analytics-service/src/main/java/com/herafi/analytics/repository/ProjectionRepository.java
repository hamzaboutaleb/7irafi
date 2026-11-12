package com.herafi.analytics.repository;

import java.sql.Timestamp;
import java.util.UUID;

import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectionRepository {
    private final JdbcTemplate jdbc;

    public ProjectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean recordEvent(UUID eventId) {
        return jdbc.update("""
                INSERT INTO analytics_processed_events(event_id)
                VALUES (?) ON CONFLICT DO NOTHING
                """, eventId) == 1;
    }
 
    public void saveJob(JobEvent.Data job) {
        jdbc.update("""
                INSERT INTO analytics_jobs(job_id, client_id, status, skill, city, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (job_id) DO UPDATE SET
                    client_id = EXCLUDED.client_id,
                    status = EXCLUDED.status,
                    skill = EXCLUDED.skill,
                    city = EXCLUDED.city,
                    created_at = EXCLUDED.created_at
                """, job.jobId(), job.clientId(), job.status().name(), job.skill(), job.city(),
                Timestamp.from(job.createdAt()));
    }

    public void deleteJob(UUID jobId) {
        jdbc.update("DELETE FROM analytics_jobs WHERE job_id = ?", jobId);
    }

    public void saveApplication(ApplicationEvent.Data application) {
        jdbc.update("""
                INSERT INTO analytics_applications(
                    application_id, job_id, applicant_id, status, job_skill, job_city, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (application_id) DO UPDATE SET
                    job_id = EXCLUDED.job_id,
                    applicant_id = EXCLUDED.applicant_id,
                    status = EXCLUDED.status,
                    job_skill = EXCLUDED.job_skill,
                    job_city = EXCLUDED.job_city,
                    submitted_at = EXCLUDED.submitted_at
                """, application.applicationId(), application.jobId(), application.applicantId(),
                application.status().name(), application.jobSkill(), application.jobCity(),
                Timestamp.from(application.submittedAt()));
    }
}

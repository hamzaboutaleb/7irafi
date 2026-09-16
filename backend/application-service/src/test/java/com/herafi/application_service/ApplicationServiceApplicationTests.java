package com.herafi.application_service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationServiceApplicationTests {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationCreatesTableAndRejectsDuplicateApplicantsForSameJob() {
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        String insert = "INSERT INTO job_applications (id, job_id, applicant_id) VALUES (?, ?, ?)";
        jdbc.update(insert, UUID.randomUUID(), jobId, applicantId);
        jdbc.update(insert, UUID.randomUUID(), jobId, UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_applications", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), jobId, applicantId))
                .isInstanceOf(DuplicateKeyException.class);
    }
}

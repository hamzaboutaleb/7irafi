package com.herafi.application_service.entity;

import java.util.UUID;

import com.herafi.application_service.event.JobLifecycleEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_lifecycle_state")
@Getter
@NoArgsConstructor
public class JobLifecycleState {

    @Id
    private UUID jobId;

    @Column(nullable = false)
    private long jobVersion = -1;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private JobLifecycleEvent.Status status;

    private UUID assignedWorkerId;

    @Column(nullable = false)
    private boolean deleted;

    public void apply(JobLifecycleEvent event) {
        jobVersion = event.jobVersion();
        status = event.data().status();
        assignedWorkerId = event.assignedWorkerId();
        deleted = event.eventType() == JobLifecycleEvent.Type.JOB_DELETED;
    }
}

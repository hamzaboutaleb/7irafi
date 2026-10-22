package com.herafi.job_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.job_service.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}

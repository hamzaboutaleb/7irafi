package com.herafi.application_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.application_service.entity.ProcessedJobEvent;

public interface ProcessedJobEventRepository extends JpaRepository<ProcessedJobEvent, UUID> {
}

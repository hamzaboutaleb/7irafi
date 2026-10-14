package com.herafi.rating_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.rating_service.entity.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Page<Rating> findByRateeIdOrderByCreatedAtDescIdDesc(UUID rateeId, Pageable pageable);

    Page<Rating> findByRaterIdOrderByCreatedAtDescIdDesc(UUID raterId, Pageable pageable);

    Page<Rating> findByJobId(UUID jobId, Pageable pageable);

    boolean existsByJobIdAndRaterIdAndRateeId(UUID jobId, UUID raterId, UUID rateeId);
}
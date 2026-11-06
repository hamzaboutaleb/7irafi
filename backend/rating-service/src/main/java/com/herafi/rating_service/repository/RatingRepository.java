package com.herafi.rating_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.rating_service.entity.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Page<Rating> findByRateeIdOrderByCreatedAtDescIdDesc(UUID rateeId, Pageable pageable);

    Page<Rating> findByRaterIdOrderByCreatedAtDescIdDesc(UUID raterId, Pageable pageable);

    Page<Rating> findByJobId(UUID jobId, Pageable pageable);

    List<Rating> findByJobIdOrderByCreatedAtDescIdDesc(UUID jobId);

    @Query("select r.score as score, count(r) as count from Rating r where r.rateeId = :userId group by r.score")
    List<ScoreCount> countScoresByRateeId(@Param("userId") UUID userId);

    interface ScoreCount {
        Short getScore();
        long getCount();
    }

    boolean existsByJobIdAndRaterIdAndRateeId(UUID jobId, UUID raterId, UUID rateeId);
}

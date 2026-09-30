package com.herafi.job_service.specification;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

import com.herafi.job_service.dto.JobFilter;
import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.PreferredTime;

public class JobSpecification {

    public static Specification<Job> isNotDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Job> hasSkill(String skill) {
        return (root, query, cb) -> skill == null ? null : cb.equal(root.get("skill"), skill);
    }

    public static Specification<Job> hasCity(String city) {
        return (root, query, cb) -> city == null ? null : cb.equal(root.get("city"), city);
    }

    public static Specification<Job> hasStatus(JobStatus status) {
        JobStatus effectiveStatus = status == null ? JobStatus.OPEN : status;
        return (root, query, cb) -> cb.equal(root.get("status"), effectiveStatus);
    }

    public static Specification<Job> minBudget(BigDecimal min) {
        return (root, query, cb) -> min == null ? null : cb.greaterThanOrEqualTo(root.get("budget"), min);
    }

    public static Specification<Job> maxBudget(BigDecimal max) {
        return (root, query, cb) -> max == null ? null : cb.lessThanOrEqualTo(root.get("budget"), max);
    }

    public static Specification<Job> hasDuration(EstimatedDuration duration) {
        return (root, query, cb) -> duration == null ? null : cb.equal(root.get("estimatedDuration"), duration);
    }

    public static Specification<Job> hasPreferredTime(PreferredTime preferredTime) {
        return (root, query, cb) -> preferredTime == null ? null : cb.equal(root.get("preferredTime"), preferredTime);
    }

    public static Specification<Job> filter(JobFilter filter) {
        return Specification
                .where(isNotDeleted())
                .and(hasSkill(filter.skill()))
                .and(hasCity(filter.city()))
                .and(hasStatus(filter.status()))
                .and(minBudget(filter.minBudget()))
                .and(maxBudget(filter.maxBudget()))
                .and(hasDuration(filter.duration()))
                .and(hasPreferredTime(filter.preferredTime()));
    }
}

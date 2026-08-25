package com.herafi.job_service.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.herafi.job_service.entity.Job;
import com.herafi.job_service.dto.JobFilter;

import jakarta.persistence.criteria.Predicate;

public class JobSpecification {

    public static Specification<Job> filter(JobFilter filter) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                cb.isFalse(root.get("deleted"))
            );

            if (filter.skill() != null) {
                predicates.add(
                    cb.equal(root.get("skill"), filter.skill())
                );
            }

            if (filter.city() != null) {
                predicates.add(
                    cb.equal(root.get("city"), filter.city())
                );
            }

            if (filter.status() != null) {
                predicates.add(
                    cb.equal(root.get("status"), filter.status())
                );
            }

            if (filter.minBudget() != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(
                        root.get("budget"),
                        filter.minBudget()
                    )
                );
            }

            if (filter.maxBudget() != null) {
                predicates.add(
                    cb.lessThanOrEqualTo(
                        root.get("budget"),
                        filter.maxBudget()
                    )
                );
            }

            if (filter.duration() != null) {
                predicates.add(
                    cb.equal(
                        root.get("estimatedDuration"),
                        filter.duration()
                    )
                );
            }

            if (filter.preferredTime() != null) {
                predicates.add(
                    cb.equal(
                        root.get("preferredTime"),
                        filter.preferredTime()
                    )
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
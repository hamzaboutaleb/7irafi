package com.herafi.job_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.job_service.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, String> {

    List<Skill> findByActiveTrueOrderByCodeAsc();

    boolean existsByCodeAndActiveTrue(String code);
}

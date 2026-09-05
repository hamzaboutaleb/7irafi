package com.herafi.job_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.job_service.entity.City;

public interface CityRepository extends JpaRepository<City, String> {

    List<City> findByActiveTrueOrderByCodeAsc();

    boolean existsByCodeAndActiveTrue(String code);
}

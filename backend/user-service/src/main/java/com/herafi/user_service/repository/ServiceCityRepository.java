package com.herafi.user_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.ServiceCity;

public interface ServiceCityRepository extends JpaRepository<String, String> {
    List<ServiceCity> findAllByActiveTrueOrderByDisplayNameAsc();

    List<ServiceCity> findAllByCodeInAndActiveTrue(Collection<String> codes);
}
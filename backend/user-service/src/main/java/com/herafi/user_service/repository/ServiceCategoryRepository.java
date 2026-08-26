package com.herafi.user_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.ServiceCategory;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, String> {

}

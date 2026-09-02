package com.herafi.user_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.ServiceCategory;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, String> {
    List<ServiceCategory> findAllByActiveTrueOrderByDisplayNameAsc();

    List<ServiceCategory> findAllByCodeInAndActiveTrue(Collection<String> codes);

}

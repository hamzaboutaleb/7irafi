package com.herafi.user_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.ServiceCategoryResponse;
import com.herafi.user_service.dto.ServiceCityResponse;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.repository.ServiceCategoryRepository;
import com.herafi.user_service.repository.ServiceCityRepository;

@Service
public class ProfessionalCatalogService {
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceCityRepository serviceCityRepository;

    public ProfessionalCatalogService(ServiceCategoryRepository serviceCategoryRepository,
            ServiceCityRepository serviceCityRepository) {
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.serviceCityRepository = serviceCityRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> getActiveCategories() {
        List<ServiceCategory> categories = serviceCategoryRepository.findAllByActiveTrueOrderByDisplayNameAsc();

        return categories.stream().map(ServiceCategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceCityResponse> getActiveCities() {
        List<ServiceCity> cities = serviceCityRepository.findAllByActiveTrueOrderByDisplayNameAsc();

        return cities.stream().map(ServiceCityResponse::from).toList();
    }
}

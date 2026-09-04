package com.herafi.user_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.ServiceCategoryResponse;
import com.herafi.user_service.dto.ServiceCityResponse;
import com.herafi.user_service.service.ProfessionalCatalogService;

@RestController
@RequestMapping("/users")
public class ProfessionalCatalogController {
    private final ProfessionalCatalogService professionalCatalogService;

    public ProfessionalCatalogController(ProfessionalCatalogService professionalCatalogService) {
        this.professionalCatalogService = professionalCatalogService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<ServiceCategoryResponse>> getCategories() {
        return ApiResponse.success(professionalCatalogService.getActiveCategories());
    }

    @GetMapping("/cities")
    public ApiResponse<List<ServiceCityResponse>> getCities() {
        return ApiResponse.success(professionalCatalogService.getActiveCities());
    }
}

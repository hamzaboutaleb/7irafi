package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.herafi.user_service.dto.ServiceCategoryResponse;
import com.herafi.user_service.dto.ServiceCityResponse;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.repository.ServiceCategoryRepository;
import com.herafi.user_service.repository.ServiceCityRepository;

class ProfessionalCatalogServiceTests {

    private ServiceCategoryRepository serviceCategoryRepository;
    private ServiceCityRepository serviceCityRepository;
    private ProfessionalCatalogService service;

    @BeforeEach
    void setUp() {
        serviceCategoryRepository = mock(ServiceCategoryRepository.class);
        serviceCityRepository = mock(ServiceCityRepository.class);
        service = new ProfessionalCatalogService(serviceCategoryRepository, serviceCityRepository);
    }

    @Test
    void mapsActiveCategoriesInRepositoryOrder() {
        ServiceCategory electrical = category(
                "ELECTRICAL",
                "Electrical",
                "Electrical installation and repair");
        ServiceCategory painting = category(
                "PAINTING",
                "Painting",
                "Interior and exterior painting");
        when(serviceCategoryRepository.findAllByActiveTrueOrderByDisplayNameAsc())
                .thenReturn(List.of(electrical, painting));

        List<ServiceCategoryResponse> result = service.getActiveCategories();

        assertEquals(List.of(
                new ServiceCategoryResponse(
                        "ELECTRICAL",
                        "Electrical",
                        "Electrical installation and repair"),
                new ServiceCategoryResponse(
                        "PAINTING",
                        "Painting",
                        "Interior and exterior painting")), result);
        verify(serviceCategoryRepository).findAllByActiveTrueOrderByDisplayNameAsc();
    }

    @Test
    void mapsActiveCitiesInRepositoryOrder() {
        ServiceCity casablanca = city("CASABLANCA", "Casablanca");
        ServiceCity rabat = city("RABAT", "Rabat");
        when(serviceCityRepository.findAllByActiveTrueOrderByDisplayNameAsc())
                .thenReturn(List.of(casablanca, rabat));

        List<ServiceCityResponse> result = service.getActiveCities();

        assertEquals(List.of(
                new ServiceCityResponse("CASABLANCA", "Casablanca"),
                new ServiceCityResponse("RABAT", "Rabat")), result);
        verify(serviceCityRepository).findAllByActiveTrueOrderByDisplayNameAsc();
    }

    private ServiceCategory category(String code, String displayName, String description) {
        ServiceCategory category = new ServiceCategory();
        category.setCode(code);
        category.setDisplayName(displayName);
        category.setDescription(description);
        category.setActive(true);
        return category;
    }

    private ServiceCity city(String code, String displayName) {
        ServiceCity city = new ServiceCity();
        city.setCode(code);
        city.setDisplayName(displayName);
        city.setActive(true);
        return city;
    }
}

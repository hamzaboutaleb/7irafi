package com.herafi.user_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.dto.ServiceCategoryResponse;
import com.herafi.user_service.dto.ServiceCityResponse;
import com.herafi.user_service.service.ProfessionalCatalogService;

@ActiveProfiles("test")
@WebMvcTest(ProfessionalCatalogController.class)
class ProfessionalCatalogControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalCatalogService professionalCatalogService;

    @Test
    void returnsActiveCategoriesInApiEnvelope() throws Exception {
        when(professionalCatalogService.getActiveCategories()).thenReturn(List.of(
                new ServiceCategoryResponse("ELECTRICAL", "Electrical", "Electrical services"),
                new ServiceCategoryResponse("PAINTING", "Painting", "Painting services")));

        mockMvc.perform(get("/users/categories")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("ELECTRICAL"))
                .andExpect(jsonPath("$.data[1].code").value("PAINTING"));

        verify(professionalCatalogService).getActiveCategories();
    }

    @Test
    void returnsActiveCitiesInApiEnvelope() throws Exception {
        when(professionalCatalogService.getActiveCities()).thenReturn(List.of(
                new ServiceCityResponse("CASABLANCA", "Casablanca"),
                new ServiceCityResponse("RABAT", "Rabat")));

        mockMvc.perform(get("/users/cities")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("CASABLANCA"))
                .andExpect(jsonPath("$.data[1].code").value("RABAT"));

        verify(professionalCatalogService).getActiveCities();
    }
}

package com.herafi.user_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.dto.UpdateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.exception.InvalidCategoryException;
import com.herafi.user_service.exception.ProfessionalProfileAlreadyExistsException;
import com.herafi.user_service.exception.ProfessionalProfileNotFoundException;
import com.herafi.user_service.service.ProfessionalProfileService;

@ActiveProfiles("test")
@WebMvcTest(ProfessionalProfileController.class)
class ProfessionalProfileControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalProfileService professionalProfileService;

    @Test
    void createsProfessionalProfileAndReturnsSelections() throws Exception {
        UUID userId = UUID.randomUUID();
        String biography = "I'm an experienced residential painter.";
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                biography,
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));
        ProfessionalProfile profile = professionalProfile(userId, biography);

        when(professionalProfileService.create(userId, request)).thenReturn(profile);

        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "I'm an experienced residential painter.",
                          "categoryCodes": ["PAINTING"],
                          "cityCodes": ["CASABLANCA"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.biography").value(biography))
                .andExpect(jsonPath("$.data.onboardingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.categories[0].code").value("PAINTING"))
                .andExpect(jsonPath("$.data.cities[0].code").value("CASABLANCA"));

        verify(professionalProfileService).create(userId, request);
    }

    @Test
    void getsCurrentProfessionalProfileWithSelections() throws Exception {
        UUID userId = UUID.randomUUID();
        String biography = "I'm an experienced residential painter.";
        ProfessionalProfile profile = professionalProfile(userId, biography);

        when(professionalProfileService.get(userId)).thenReturn(profile);

        mockMvc.perform(get("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.biography").value(biography))
                .andExpect(jsonPath("$.data.categories[0].displayName").value("Painting"))
                .andExpect(jsonPath("$.data.cities[0].displayName").value("Casablanca"));

        verify(professionalProfileService).get(userId);
    }

    @Test
    void updatesCurrentProfessionalProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        String biography = "Updated professional biography.";
        UpdateProfessionalProfileRequest request = new UpdateProfessionalProfileRequest(
                biography,
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));
        ProfessionalProfile profile = professionalProfile(userId, biography);

        when(professionalProfileService.update(userId, request)).thenReturn(profile);

        mockMvc.perform(put("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "Updated professional biography.",
                          "categoryCodes": ["PAINTING"],
                          "cityCodes": ["CASABLANCA"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.biography").value(biography))
                .andExpect(jsonPath("$.data.onboardingStatus").value("ACTIVE"));

        verify(professionalProfileService).update(userId, request);
    }

    @Test
    void rejectsBlankCategoryCodeBeforeCallingService() throws Exception {
        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "I'm an experienced residential painter.",
                          "categoryCodes": ["   "],
                          "cityCodes": ["CASABLANCA"]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(professionalProfileService);
    }

    @Test
    void rejectsEmptyCitySelectionBeforeCallingService() throws Exception {
        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "I'm an experienced residential painter.",
                          "categoryCodes": ["PAINTING"],
                          "cityCodes": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(professionalProfileService);
    }

    @Test
    void rejectsBlankCityCodeOnUpdateBeforeCallingService() throws Exception {
        mockMvc.perform(put("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "Updated professional biography.",
                          "categoryCodes": ["PAINTING"],
                          "cityCodes": ["   "]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(professionalProfileService);
    }

    @Test
    void returnsConflictWhenProfessionalProfileAlreadyExists() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "I'm an experienced residential painter.",
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));
        when(professionalProfileService.create(userId, request))
                .thenThrow(new ProfessionalProfileAlreadyExistsException(userId));

        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "I'm an experienced residential painter.",
                          "categoryCodes": ["PAINTING"],
                          "cityCodes": ["CASABLANCA"]
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFESSIONAL_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    void returnsNotFoundWhenProfessionalProfileIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        when(professionalProfileService.get(userId))
                .thenThrow(new ProfessionalProfileNotFoundException(userId));

        mockMvc.perform(get("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROFESSIONAL_PROFILE_NOT_FOUND"));
    }

    @Test
    void returnsBadRequestForInvalidCatalogCode() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "I'm an experienced residential painter.",
                Set.of("UNKNOWN"),
                Set.of("CASABLANCA"));
        when(professionalProfileService.create(userId, request))
                .thenThrow(new InvalidCategoryException("Unknown or inactive category codes: [UNKNOWN]"));

        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "biography": "I'm an experienced residential painter.",
                          "categoryCodes": ["UNKNOWN"],
                          "cityCodes": ["CASABLANCA"]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CATALOG_CODE"));
    }

    private ProfessionalProfile professionalProfile(UUID userId, String biography) {
        ServiceCategory category = new ServiceCategory();
        category.setCode("PAINTING");
        category.setDisplayName("Painting");
        category.setDescription("Interior and exterior painting");

        ServiceCity city = new ServiceCity();
        city.setCode("CASABLANCA");
        city.setDisplayName("Casablanca");

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setBiography(biography);
        profile.setOnboardingStatus(ProfessionalOnboardingStatus.ACTIVE);
        profile.setCategories(Set.of(category));
        profile.setCities(Set.of(city));
        return profile;
    }
}

package com.herafi.user_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.service.ProfessionalProfileService;

@ActiveProfiles("test")
@WebMvcTest(ProfessionalProfileController.class)
public class ProfessionalProfileControllerTests {
    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalProfileService professionalProfileService;

    @Test
    void createsProfessionalProfileAndReturnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        String biography = "I'm a great painter";
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(biography);

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setBiography(biography);
        profile.setOnboardingStatus(ProfessionalOnboardingStatus.INCOMPLETE);

        when(professionalProfileService.create(userId, request))
                .thenReturn(profile);

        mockMvc.perform(post("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "biography" : "I'm a great painter"
                        }
                        """))

                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.biography").value(biography))
                .andExpect(jsonPath("$.data.onboardingStatus").value(ProfessionalOnboardingStatus.INCOMPLETE.name()));
        verify(professionalProfileService).create(userId, request);
    }

    @Test
    void getsCurrentProfessionalProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        String biography = "I'm a great painter";

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setBiography(biography);
        profile.setOnboardingStatus(ProfessionalOnboardingStatus.INCOMPLETE);

        when(professionalProfileService.get(userId))
                .thenReturn(profile);

        mockMvc.perform(get("/users/me/professional-profile")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.biography").value(biography))
                .andExpect(jsonPath("$.data.onboardingStatus").value(ProfessionalOnboardingStatus.INCOMPLETE.name()));
        verify(professionalProfileService).get(userId);
    }

}

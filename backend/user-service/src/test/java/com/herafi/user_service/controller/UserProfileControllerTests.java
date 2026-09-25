package com.herafi.user_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.AvatarService;
import com.herafi.user_service.service.UserProfileService;

@ActiveProfiles("test")
@WebMvcTest(UserProfileController.class)
class UserProfileControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    // The controller resolves avatar links through it; storage is not part of these tests.
    @MockitoBean
    private AvatarService avatarService;

    @Test
    void rejectsProfileUpdateWithShortFirstName() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/users/me")
                .header("X-User-Id", userId.toString())
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName": "Jo",
                          "lastName": "Tester"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.errors[0].field").value("firstName"));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void updatesCurrentUserAndReturnsProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-18T12:00:00Z");

        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Joe", "Tester");

        UserProfile updatedProfile = new UserProfile();
        updatedProfile.setId(userId);
        updatedProfile.setEmail("joe@example.com");
        updatedProfile.setFirstName("Joe");
        updatedProfile.setLastName("Tester");
        updatedProfile.setCreatedAt(now);
        updatedProfile.setUpdatedAt(now);

        when(userProfileService.update(userId, request))
                .thenReturn(updatedProfile);

        mockMvc.perform(put("/users/me")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName": "Joe",
                          "lastName": "Tester"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("joe@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("Joe"))
                .andExpect(jsonPath("$.data.lastName").value("Tester"));

        verify(userProfileService).update(userId, request);
    }
}

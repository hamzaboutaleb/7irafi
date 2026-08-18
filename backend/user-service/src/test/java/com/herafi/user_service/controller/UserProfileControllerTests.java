package com.herafi.user_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.herafi.user_service.service.UserProfileService;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@ActiveProfiles("test")
@WebMvcTest(UserProfileController.class)
class UserProfileControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";
    
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

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
}

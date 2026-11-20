package com.herafi.auth_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerRefreshTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    // register and return the JSON response
    private JsonNode register() throws Exception {
        String body = """
                {"email":"%s@example.com","password":"password123","firstName":"Test","lastName":"User"}
                """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    // register gives a refresh token, and /auth/refresh swaps it for a new pair
    @Test
    void refreshReturnsNewTokens() throws Exception {
        String first = register().get("refreshToken").asString();
        assertThat(first).isNotBlank();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    // the same refresh token twice: the second call is refused
    @Test
    void usedRefreshTokenIsRefused() throws Exception {
        String first = register().get("refreshToken").asString();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(first)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    // after logout the refresh token no longer works
    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String token = register().get("refreshToken").asString();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(token)))
                .andExpect(status().isUnauthorized());
    }
}

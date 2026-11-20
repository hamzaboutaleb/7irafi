package com.herafi.user_service.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.service.ProfessionalProfileService;

@ActiveProfiles("test")
@WebMvcTest(InternalWorkerController.class)
class InternalWorkerControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProfessionalProfileService service;

    @Test
    void requiresInternalKeyEvenWhenCallerClaimsWorkerRole() throws Exception {
        mvc.perform(get("/internal/users/{id}/worker-eligibility", UUID.randomUUID())
                .header("X-User-Role", "WORKER"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsEligibilityForRequestedIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        for (boolean eligible : new boolean[]{true, false}) {
            when(service.isActiveWorker(userId)).thenReturn(eligible);
            mvc.perform(get("/internal/users/{id}/worker-eligibility", userId)
                    .header("X-Internal-Api-Key", "test-internal-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.eligible").value(eligible));
        }
    }
}

package com.herafi.user_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.ConnectionNotFoundException;
import com.herafi.user_service.exception.NotAProfessionalException;
import com.herafi.user_service.exception.SelfConnectionException;
import com.herafi.user_service.service.AvatarService;
import com.herafi.user_service.service.UserConnectionService;

@ActiveProfiles("test")
@WebMvcTest(UserConnectionController.class)
class UserConnectionControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserConnectionService userConnectionService;

    // The controller resolves avatar links through it; storage is not part of these tests.
    @MockitoBean
    private AvatarService avatarService;

    @Test
    void listsTheCallersNetworkAsCards() throws Exception {
        UUID owner = UUID.randomUUID();

        when(userConnectionService.getConnections(owner))
                .thenReturn(List.of(professional(Instant.now().minusSeconds(5))));

        mockMvc.perform(get("/users/me/connections")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].firstName").value("Amine"))
                .andExpect(jsonPath("$.data[0].categories[0].code").value("PLUMBING"))
                .andExpect(jsonPath("$.data[0].online").value(true))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void takesTheOwnerFromTheGatewayHeaderNotThePath() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        mockMvc.perform(post("/users/me/connections/" + target)
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", owner.toString()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<UUID> ownerCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> targetCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userConnectionService).addConnection(ownerCaptor.capture(), targetCaptor.capture());

        assertEquals(owner, ownerCaptor.getValue());
        assertEquals(target, targetCaptor.getValue());
    }

    @Test
    void removingReturnsNoContent() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        mockMvc.perform(delete("/users/me/connections/" + target)
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", owner.toString()))
                .andExpect(status().isNoContent());

        verify(userConnectionService).removeConnection(owner, target);
    }

    @Test
    void savingYourselfIsABadRequestWithItsOwnCode() throws Exception {
        doThrow(new SelfConnectionException())
                .when(userConnectionService).addConnection(any(), any());

        mockMvc.perform(post("/users/me/connections/" + UUID.randomUUID())
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SELF_CONNECTION_NOT_ALLOWED"));
    }

    @Test
    void savingANonProfessionalIsABadRequestWithItsOwnCode() throws Exception {
        UUID target = UUID.randomUUID();

        doThrow(new NotAProfessionalException(target))
                .when(userConnectionService).addConnection(any(), any());

        mockMvc.perform(post("/users/me/connections/" + target)
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NOT_A_PROFESSIONAL"));
    }

    @Test
    void removingSomethingAbsentIsNotFoundWithItsOwnCode() throws Exception {
        UUID target = UUID.randomUUID();

        doThrow(new ConnectionNotFoundException(target))
                .when(userConnectionService).removeConnection(any(), any());

        mockMvc.perform(delete("/users/me/connections/" + target)
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONNECTION_NOT_FOUND"));
    }

    private ProfessionalProfile professional(Instant lastSeenAt) {
        UUID userId = UUID.randomUUID();

        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setFirstName("Amine");
        userProfile.setEmail("amine@example.com");
        userProfile.setLastSeenAt(lastSeenAt);

        ServiceCategory category = new ServiceCategory();
        category.setCode("PLUMBING");
        category.setDisplayName("Plumbing");
        category.setActive(true);

        ServiceCity city = new ServiceCity();
        city.setCode("KENITRA");
        city.setDisplayName("Kenitra");
        city.setActive(true);

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setUserProfile(userProfile);
        profile.setOnboardingStatus(ProfessionalOnboardingStatus.ACTIVE);
        profile.setCategories(Set.of(category));
        profile.setCities(Set.of(city));
        return profile;
    }
}

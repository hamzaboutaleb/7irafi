package com.herafi.user_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.ProfessionalProfileService;

@ActiveProfiles("test")
@WebMvcTest(ProfessionalDirectoryController.class)
class ProfessionalDirectoryControllerTests {

    private static final String TEST_GATEWAY_KEY = "test-gateway-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfessionalProfileService professionalProfileService;

    @Test
    void returnsMappedProfessionalCardsAndPaginationMetadata() throws Exception {
        ProfessionalProfile profile = professionalProfile();
        Pageable returnedPageable = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "userId"));

        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(profile), returnedPageable, 5));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .queryParam("page", "1")
                .queryParam("size", "2")
                .queryParam("sort", "userId,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].userId").value(profile.getUserId().toString()))
                .andExpect(jsonPath("$.data.content[0].firstName").value("Youssef"))
                .andExpect(jsonPath("$.data.content[0].categories[0].code").value("PAINTING"))
                .andExpect(jsonPath("$.data.content[0].cities[0].code").value("CASABLANCA"))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(professionalProfileService).getActiveProfessionals(pageableCaptor.capture(), any(), any());

        Pageable requestedPageable = pageableCaptor.getValue();
        assertEquals(1, requestedPageable.getPageNumber());
        assertEquals(2, requestedPageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                requestedPageable.getSort().getOrderFor("userId").getDirection());
    }

    @Test
    void usesDirectoryPaginationDefaults() throws Exception {
        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(),
                        invocation.getArgument(0),
                        0));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(professionalProfileService).getActiveProfessionals(pageableCaptor.capture(), any(), any());

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(12, pageable.getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageable.getSort().getOrderFor("userId").getDirection());
    }

    @Test
    void capsRequestedPageSizeAtConfiguredMaximum() throws Exception {
        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(),
                        invocation.getArgument(0),
                        0));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(professionalProfileService).getActiveProfessionals(pageableCaptor.capture(), any(), any());

        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void marksAProfessionalSeenRecentlyAsOnline() throws Exception {
        ProfessionalProfile profile = professionalProfile(Instant.now().minusSeconds(10));

        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(profile), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].online").value(true));
    }

    @Test
    void marksAProfessionalSeenLongAgoAsOffline() throws Exception {
        ProfessionalProfile profile = professionalProfile(
                Instant.now().minus(Duration.ofHours(1)));

        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(profile), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].online").value(false));
    }

    @Test
    void marksAProfessionalNeverSeenAsOffline() throws Exception {
        ProfessionalProfile profile = professionalProfile(null);

        when(professionalProfileService.getActiveProfessionals(any(Pageable.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(profile), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/users/professionals")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].online").value(false));
    }

    private ProfessionalProfile professionalProfile() {
        return professionalProfile(null);
    }

    private ProfessionalProfile professionalProfile(Instant lastSeenAt) {
        UUID userId = UUID.randomUUID();

        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setFirstName("Youssef");
        userProfile.setEmail("youssef@example.com");
        userProfile.setLastSeenAt(lastSeenAt);

        ServiceCategory category = new ServiceCategory();
        category.setCode("PAINTING");
        category.setDisplayName("Painting");
        category.setDescription("Interior and exterior painting");
        category.setActive(true);

        ServiceCity city = new ServiceCity();
        city.setCode("CASABLANCA");
        city.setDisplayName("Casablanca");
        city.setActive(true);

        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setUserProfile(userProfile);
        profile.setBiography("Experienced residential painter.");
        profile.setOnboardingStatus(ProfessionalOnboardingStatus.ACTIVE);
        profile.setCategories(Set.of(category));
        profile.setCities(Set.of(city));
        return profile;
    }
}

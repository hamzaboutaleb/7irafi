package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.dto.UpdateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.InvalidCategoryException;
import com.herafi.user_service.exception.InvalidCityException;
import com.herafi.user_service.exception.ProfessionalProfileAlreadyExistsException;
import com.herafi.user_service.exception.ProfessionalProfileNotFoundException;
import com.herafi.user_service.repository.ProfessionalProfileRepository;
import com.herafi.user_service.repository.ServiceCategoryRepository;
import com.herafi.user_service.repository.ServiceCityRepository;
import com.herafi.user_service.repository.UserProfileRepository;

class ProfessionalProfileServiceTests {

    private UserProfileRepository userProfileRepository;
    private ProfessionalProfileRepository professionalProfileRepository;
    private ServiceCategoryRepository serviceCategoryRepository;
    private ServiceCityRepository serviceCityRepository;
    private ProfessionalProfileService service;

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        professionalProfileRepository = mock(ProfessionalProfileRepository.class);
        serviceCategoryRepository = mock(ServiceCategoryRepository.class);
        serviceCityRepository = mock(ServiceCityRepository.class);
        service = new ProfessionalProfileService(
                userProfileRepository,
                professionalProfileRepository,
                serviceCategoryRepository,
                serviceCityRepository);
    }

    @Test
    void createsCompleteProfileWithNormalizedSelectionsAndActivatesIt() {
        UUID userId = UUID.randomUUID();
        UserProfile userProfile = userProfile(userId);
        ServiceCategory category = category("PAINTING");
        ServiceCity city = city("CASABLANCA");
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "  Experienced residential painter.  ",
                Set.of(" painting "),
                Set.of(" casablanca "));

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile));
        when(professionalProfileRepository.existsById(userId)).thenReturn(false);
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("PAINTING")))
                .thenReturn(List.of(category));
        when(serviceCityRepository.findAllByCodeInAndActiveTrue(Set.of("CASABLANCA")))
                .thenReturn(List.of(city));
        when(professionalProfileRepository.save(any(ProfessionalProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfessionalProfile created = service.create(userId, request);

        assertEquals("Experienced residential painter.", created.getBiography());
        assertEquals(ProfessionalOnboardingStatus.ACTIVE, created.getOnboardingStatus());
        assertEquals(Set.of(category), created.getCategories());
        assertEquals(Set.of(city), created.getCities());
        assertSame(userProfile, created.getUserProfile());
        verify(professionalProfileRepository).save(created);
    }

    @Test
    void rejectsDuplicateProfessionalProfile() {
        UUID userId = UUID.randomUUID();
        CreateProfessionalProfileRequest request = validCreateRequest();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile(userId)));
        when(professionalProfileRepository.existsById(userId)).thenReturn(true);

        assertThrows(
                ProfessionalProfileAlreadyExistsException.class,
                () -> service.create(userId, request));

        verifyNoInteractions(serviceCategoryRepository, serviceCityRepository);
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void rejectsUnknownOrInactiveCategoryCode() {
        UUID userId = UUID.randomUUID();
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "Experienced residential painter.",
                Set.of("UNKNOWN"),
                Set.of("CASABLANCA"));

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile(userId)));
        when(professionalProfileRepository.existsById(userId)).thenReturn(false);
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("UNKNOWN")))
                .thenReturn(List.of());

        InvalidCategoryException exception = assertThrows(
                InvalidCategoryException.class,
                () -> service.create(userId, request));

        assertTrue(exception.getMessage().contains("UNKNOWN"));
        verifyNoInteractions(serviceCityRepository);
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void rejectsUnknownOrInactiveCityCode() {
        UUID userId = UUID.randomUUID();
        ServiceCategory category = category("PAINTING");
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "Experienced residential painter.",
                Set.of("PAINTING"),
                Set.of("UNKNOWN"));

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile(userId)));
        when(professionalProfileRepository.existsById(userId)).thenReturn(false);
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("PAINTING")))
                .thenReturn(List.of(category));
        when(serviceCityRepository.findAllByCodeInAndActiveTrue(Set.of("UNKNOWN")))
                .thenReturn(List.of());

        InvalidCityException exception = assertThrows(
                InvalidCityException.class,
                () -> service.create(userId, request));

        assertTrue(exception.getMessage().contains("UNKNOWN"));
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void rejectsEmptyCategorySelectionAtServiceBoundary() {
        UUID userId = UUID.randomUUID();
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "Experienced residential painter.",
                Set.of(),
                Set.of("CASABLANCA"));

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile(userId)));
        when(professionalProfileRepository.existsById(userId)).thenReturn(false);

        assertThrows(InvalidCategoryException.class, () -> service.create(userId, request));
        verifyNoInteractions(serviceCategoryRepository, serviceCityRepository);
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void rejectsEmptyCitySelectionAtServiceBoundary() {
        UUID userId = UUID.randomUUID();
        ServiceCategory category = category("PAINTING");
        CreateProfessionalProfileRequest request = new CreateProfessionalProfileRequest(
                "Experienced residential painter.",
                Set.of("PAINTING"),
                Set.of());

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile(userId)));
        when(professionalProfileRepository.existsById(userId)).thenReturn(false);
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("PAINTING")))
                .thenReturn(List.of(category));

        assertThrows(InvalidCityException.class, () -> service.create(userId, request));
        verifyNoInteractions(serviceCityRepository);
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void replacesSelectionsAndActivatesIncompleteProfileOnUpdate() {
        UUID userId = UUID.randomUUID();
        ServiceCategory oldCategory = category("PLUMBING");
        ServiceCity oldCity = city("RABAT");
        ServiceCategory newCategory = category("PAINTING");
        ServiceCity newCity = city("CASABLANCA");
        ProfessionalProfile existing = professionalProfile(
                userId,
                ProfessionalOnboardingStatus.INCOMPLETE,
                Set.of(oldCategory),
                Set.of(oldCity));
        UpdateProfessionalProfileRequest request = new UpdateProfessionalProfileRequest(
                "  Updated professional biography.  ",
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));

        when(professionalProfileRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("PAINTING")))
                .thenReturn(List.of(newCategory));
        when(serviceCityRepository.findAllByCodeInAndActiveTrue(Set.of("CASABLANCA")))
                .thenReturn(List.of(newCity));
        when(professionalProfileRepository.save(existing)).thenReturn(existing);

        ProfessionalProfile updated = service.update(userId, request);

        assertEquals("Updated professional biography.", updated.getBiography());
        assertEquals(Set.of(newCategory), updated.getCategories());
        assertEquals(Set.of(newCity), updated.getCities());
        assertEquals(ProfessionalOnboardingStatus.ACTIVE, updated.getOnboardingStatus());
    }

    @Test
    void keepsSuspendedProfileSuspendedDuringOrdinaryUpdate() {
        UUID userId = UUID.randomUUID();
        ServiceCategory category = category("PAINTING");
        ServiceCity city = city("CASABLANCA");
        ProfessionalProfile suspended = professionalProfile(
                userId,
                ProfessionalOnboardingStatus.SUSPENDED,
                Set.of(category),
                Set.of(city));
        UpdateProfessionalProfileRequest request = new UpdateProfessionalProfileRequest(
                "Updated professional biography.",
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));

        when(professionalProfileRepository.findById(userId)).thenReturn(Optional.of(suspended));
        when(serviceCategoryRepository.findAllByCodeInAndActiveTrue(Set.of("PAINTING")))
                .thenReturn(List.of(category));
        when(serviceCityRepository.findAllByCodeInAndActiveTrue(Set.of("CASABLANCA")))
                .thenReturn(List.of(city));
        when(professionalProfileRepository.save(suspended)).thenReturn(suspended);

        ProfessionalProfile updated = service.update(userId, request);

        assertEquals(ProfessionalOnboardingStatus.SUSPENDED, updated.getOnboardingStatus());
        assertEquals("Updated professional biography.", updated.getBiography());
    }

    @Test
    void rejectsUpdateWhenProfessionalProfileIsMissing() {
        UUID userId = UUID.randomUUID();
        when(professionalProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                ProfessionalProfileNotFoundException.class,
                () -> service.update(userId, validUpdateRequest()));

        verifyNoInteractions(serviceCategoryRepository, serviceCityRepository);
        verify(professionalProfileRepository, never()).save(any(ProfessionalProfile.class));
    }

    @Test
    void retrievesProfileThroughDetailedRepositoryQuery() {
        UUID userId = UUID.randomUUID();
        ProfessionalProfile profile = professionalProfile(
                userId,
                ProfessionalOnboardingStatus.ACTIVE,
                Set.of(category("PAINTING")),
                Set.of(city("CASABLANCA")));
        when(professionalProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        ProfessionalProfile result = service.get(userId);

        assertSame(profile, result);
        verify(professionalProfileRepository).findByUserId(userId);
    }

    @Test
    void retrievesOnlyActiveProfessionalsUsingRequestedPage() {
        Pageable pageable = PageRequest.of(1, 12);
        Page<ProfessionalProfile> expected = directoryPage(pageable);

        when(professionalProfileRepository.findDirectoryPage(
                ProfessionalOnboardingStatus.ACTIVE,
                null,
                null,
                pageable))
                .thenReturn(expected);

        Page<ProfessionalProfile> result = service.getActiveProfessionals(pageable, null, null);

        assertSame(expected, result);
        verify(professionalProfileRepository).findDirectoryPage(
                ProfessionalOnboardingStatus.ACTIVE,
                null,
                null,
                pageable);
    }

    @Test
    void passesCategoryAndCityFiltersToTheRepository() {
        Pageable pageable = PageRequest.of(0, 12);
        Page<ProfessionalProfile> expected = directoryPage(pageable);

        when(professionalProfileRepository.findDirectoryPage(
                ProfessionalOnboardingStatus.ACTIVE,
                "PAINTING",
                "CASABLANCA",
                pageable))
                .thenReturn(expected);

        Page<ProfessionalProfile> result =
                service.getActiveProfessionals(pageable, "PAINTING", "CASABLANCA");

        assertSame(expected, result);
        verify(professionalProfileRepository).findDirectoryPage(
                ProfessionalOnboardingStatus.ACTIVE,
                "PAINTING",
                "CASABLANCA",
                pageable);
    }

    @Test
    void neverLetsACallerRequestANonActiveStatus() {
        Pageable pageable = PageRequest.of(0, 12);
        ArgumentCaptor<ProfessionalOnboardingStatus> statusCaptor =
                ArgumentCaptor.forClass(ProfessionalOnboardingStatus.class);

        when(professionalProfileRepository.findDirectoryPage(
                any(ProfessionalOnboardingStatus.class),
                any(),
                any(),
                any(Pageable.class)))
                .thenReturn(directoryPage(pageable));

        service.getActiveProfessionals(pageable, "PAINTING", null);

        verify(professionalProfileRepository).findDirectoryPage(
                statusCaptor.capture(),
                any(),
                any(),
                any(Pageable.class));

        assertSame(ProfessionalOnboardingStatus.ACTIVE, statusCaptor.getValue());
    }

    private Page<ProfessionalProfile> directoryPage(Pageable pageable) {
        ProfessionalProfile profile = professionalProfile(
                UUID.randomUUID(),
                ProfessionalOnboardingStatus.ACTIVE,
                Set.of(category("PAINTING")),
                Set.of(city("CASABLANCA")));

        return new PageImpl<>(List.of(profile), pageable, 13);
    }

    private CreateProfessionalProfileRequest validCreateRequest() {
        return new CreateProfessionalProfileRequest(
                "Experienced residential painter.",
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));
    }

    private UpdateProfessionalProfileRequest validUpdateRequest() {
        return new UpdateProfessionalProfileRequest(
                "Updated professional biography.",
                Set.of("PAINTING"),
                Set.of("CASABLANCA"));
    }

    private UserProfile userProfile(UUID userId) {
        UserProfile profile = new UserProfile();
        profile.setId(userId);
        return profile;
    }

    private ProfessionalProfile professionalProfile(
            UUID userId,
            ProfessionalOnboardingStatus status,
            Set<ServiceCategory> categories,
            Set<ServiceCity> cities) {
        ProfessionalProfile profile = new ProfessionalProfile();
        profile.setUserId(userId);
        profile.setBiography("Existing professional biography.");
        profile.setOnboardingStatus(status);
        profile.setCategories(categories);
        profile.setCities(cities);
        return profile;
    }

    private ServiceCategory category(String code) {
        ServiceCategory category = new ServiceCategory();
        category.setCode(code);
        category.setDisplayName(code);
        category.setDescription(code + " services");
        category.setActive(true);
        return category;
    }

    private ServiceCity city(String code) {
        ServiceCity city = new ServiceCity();
        city.setCode(code);
        city.setDisplayName(code);
        city.setActive(true);
        return city;
    }
}

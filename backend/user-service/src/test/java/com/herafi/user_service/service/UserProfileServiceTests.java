package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.UserProfileAlreadyExistsException;
import com.herafi.user_service.exception.UserProfileNotFoundException;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class UserProfileServiceTests {

    @Autowired
    private UserProfileService service;

    @Test
    void createsNormalizedProfile() {
        UUID id = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                id,
                "  USER@Example.com ",
                " Youssef ",
                " Rachidi "
        );

        UserProfile profile = service.create(request);

        assertEquals(id, profile.getId());
        assertEquals("user@example.com", profile.getEmail());
        assertEquals("Youssef", profile.getFirstName());
        assertEquals("Rachidi", profile.getLastName());
    }

    @Test
    void rejectsDuplicateProfileId() {
        UUID id = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                id,
                "user@example.com",
                "Youssef",
                "Rachidi"
        );

        service.create(request);

        assertThrows(UserProfileAlreadyExistsException.class, () -> service.create(request));
    }

    @Test
    void updatesExistingProfile() {
        UUID id = UUID.randomUUID();
        service.create(new CreateUserProfileRequest(
                id,
                "user@example.com",
                "Old",
                "Name"
        ));

        UserProfile updated = service.update(
                id,
                new UpdateUserProfileRequest(" New ", " Name ")
        );

        assertEquals("New", updated.getFirstName());
        assertEquals("Name", updated.getLastName());
    }

    @Test
    void rejectsMissingProfile() {
        UUID id = UUID.randomUUID();

        UserProfileNotFoundException exception = assertThrows(
                UserProfileNotFoundException.class,
                () -> service.get(id)
        );

        assertTrue(exception.getMessage().contains(id.toString()));
    }
}

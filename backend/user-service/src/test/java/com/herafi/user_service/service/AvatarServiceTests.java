package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.InvalidAvatarException;
import com.herafi.user_service.repository.UserProfileRepository;

class AvatarServiceTests {

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3 };
    private static final byte[] JPEG_BYTES = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2 };

    private UserProfileRepository userProfileRepository;
    private AvatarStorage avatarStorage;
    private AvatarService service;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        avatarStorage = mock(AvatarStorage.class);
        service = new AvatarService(userProfileRepository, avatarStorage, DataSize.ofMegabytes(2));

        profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("worker@7irafi.dev");
        profile.setFirstName("Worker");
        profile.setLastName("Test");
        when(userProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
    }

    @Test
    void uploadingAPngStoresItAndPointsTheProfileAtIt() {
        service.replace(profile.getId(), file("avatar.png", "image/png", PNG_BYTES));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).upload(key.capture(), any(), anyLong(), anyString());
        assertTrue(key.getValue().startsWith(profile.getId() + "/"));
        assertTrue(key.getValue().endsWith(".png"));
        assertEquals(key.getValue(), profile.getAvatarKey());
        assertNotNull(profile.getAvatarUpdatedAt());
    }

    @Test
    void jpegIsAcceptedToo() {
        service.replace(profile.getId(), file("avatar.jpg", "image/jpeg", JPEG_BYTES));

        assertTrue(profile.getAvatarKey().endsWith(".jpg"));
    }

    @Test
    void anUnsupportedTypeIsRejected() {
        InvalidAvatarException exception = assertThrows(InvalidAvatarException.class,
                () -> service.replace(profile.getId(), file("avatar.gif", "image/gif", PNG_BYTES)));

        assertEquals("AVATAR_TYPE_NOT_SUPPORTED", exception.getCode());
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void aFileThatLiesAboutItsTypeIsRejected() {
        InvalidAvatarException exception = assertThrows(InvalidAvatarException.class,
                () -> service.replace(profile.getId(),
                        file("avatar.png", "image/png", "not really an image".getBytes())));

        assertEquals("AVATAR_TYPE_MISMATCH", exception.getCode());
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void anOversizedFileIsRejected() {
        byte[] big = new byte[(int) DataSize.ofMegabytes(3).toBytes()];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);

        InvalidAvatarException exception = assertThrows(InvalidAvatarException.class,
                () -> service.replace(profile.getId(), file("avatar.png", "image/png", big)));

        assertEquals("AVATAR_TOO_LARGE", exception.getCode());
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void anEmptyUploadIsRejected() {
        InvalidAvatarException exception = assertThrows(InvalidAvatarException.class,
                () -> service.replace(profile.getId(), file("avatar.png", "image/png", new byte[0])));

        assertEquals("AVATAR_EMPTY", exception.getCode());
    }

    @Test
    void replacingDeletesTheImageItReplaced() {
        profile.setAvatarKey("old-key");

        service.replace(profile.getId(), file("avatar.png", "image/png", PNG_BYTES));

        verify(avatarStorage).delete("old-key");
    }

    @Test
    void aFailedCleanupDoesNotFailTheUpload() {
        profile.setAvatarKey("old-key");
        org.mockito.Mockito.doThrow(new RuntimeException("storage down")).when(avatarStorage).delete("old-key");

        service.replace(profile.getId(), file("avatar.png", "image/png", PNG_BYTES));

        assertTrue(profile.getAvatarKey().endsWith(".png"));
    }

    @Test
    void removingClearsTheProfileAndDeletesTheObject() {
        profile.setAvatarKey("some-key");

        service.remove(profile.getId());

        assertNull(profile.getAvatarKey());
        assertNull(profile.getAvatarUpdatedAt());
        verify(avatarStorage).delete("some-key");
    }

    @Test
    void removingWithoutAnAvatarDoesNothing() {
        service.remove(profile.getId());

        verify(avatarStorage, never()).delete(anyString());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void thereIsNoUrlWithoutAnAvatar() {
        assertNull(service.urlFor(profile));
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void theUrlIsSignedByStorage() {
        profile.setAvatarKey("some-key");
        when(avatarStorage.downloadUrl("some-key")).thenReturn("http://minio/signed");

        assertEquals("http://minio/signed", service.urlFor(profile));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}

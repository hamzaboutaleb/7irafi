package com.herafi.user_service.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.InvalidAvatarException;
import com.herafi.user_service.exception.UserProfileNotFoundException;
import com.herafi.user_service.repository.UserProfileRepository;

/**
 * Avatar upload, replacement and removal.
 *
 * The browser checks the file too, but that check is a courtesy to the user: it
 * can be bypassed with one curl, so every rule is enforced again here. The
 * declared content type is not trusted either -- the first bytes of the file
 * decide what it really is.
 */
@Service
public class AvatarService {

    /** Magic bytes, in upload order: PNG, JPEG, WebP ("RIFF....WEBP"). */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G' },
            "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
            "image/webp", new byte[] { 'R', 'I', 'F', 'F' });

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    private final UserProfileRepository userProfileRepository;
    private final AvatarStorage avatarStorage;
    private final long maxBytes;

    public AvatarService(
            UserProfileRepository userProfileRepository,
            AvatarStorage avatarStorage,
            @Value("${users.avatar.max-file-size}") DataSize maxFileSize) {
        this.userProfileRepository = userProfileRepository;
        this.avatarStorage = avatarStorage;
        this.maxBytes = maxFileSize.toBytes();
    }

    @Transactional
    public UserProfile replace(UUID userId, MultipartFile file) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        String contentType = validate(file);
        String previousKey = profile.getAvatarKey();
        // A new key every time rather than overwriting: a cached or in-flight
        // link to the old image can never resolve to the new one.
        String key = "%s/%s.%s".formatted(userId, UUID.randomUUID(), EXTENSIONS.get(contentType));

        try (var content = file.getInputStream()) {
            avatarStorage.upload(key, content, file.getSize(), contentType);
        } catch (IOException exception) {
            throw new InvalidAvatarException("AVATAR_UNREADABLE", "The uploaded file could not be read");
        }

        profile.setAvatarKey(key);
        profile.setAvatarUpdatedAt(Instant.now());
        userProfileRepository.save(profile);

        // Only after the row points at the new image. The reverse order would
        // leave a profile pointing at a deleted object if the save failed.
        deleteQuietly(previousKey);

        return profile;
    }

    @Transactional
    public void remove(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        String key = profile.getAvatarKey();
        if (key == null) {
            return;
        }

        profile.setAvatarKey(null);
        profile.setAvatarUpdatedAt(null);
        userProfileRepository.save(profile);

        deleteQuietly(key);
    }

    /** A short-lived link, or null when the person has no avatar. */
    public String urlFor(UserProfile profile) {
        String key = profile.getAvatarKey();
        return key == null ? null : avatarStorage.downloadUrl(key);
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException("AVATAR_EMPTY", "No file was uploaded");
        }

        if (file.getSize() > maxBytes) {
            throw new InvalidAvatarException("AVATAR_TOO_LARGE",
                    "The image must be %d MB or smaller".formatted(maxBytes / (1024 * 1024)));
        }

        String declared = file.getContentType();
        if (declared == null || !SIGNATURES.containsKey(declared)) {
            throw new InvalidAvatarException("AVATAR_TYPE_NOT_SUPPORTED",
                    "Only PNG, JPEG and WebP images are accepted");
        }

        byte[] expected = SIGNATURES.get(declared);
        byte[] actual = new byte[expected.length];

        try (var content = file.getInputStream()) {
            if (content.read(actual) != expected.length || !Arrays.equals(expected, actual)) {
                throw new InvalidAvatarException("AVATAR_TYPE_MISMATCH",
                        "The file is not really a %s image".formatted(declared));
            }
        } catch (IOException exception) {
            throw new InvalidAvatarException("AVATAR_UNREADABLE", "The uploaded file could not be read");
        }

        return declared;
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }

        try {
            avatarStorage.delete(key);
        } catch (RuntimeException exception) {
            // The profile is already correct. A leftover object costs storage,
            // not correctness, so it must not fail the request.
        }
    }
}

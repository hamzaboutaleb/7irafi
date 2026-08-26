package com.herafi.user_service.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;

class UpdateUserProfileRequestValidationTests {
    @Test
    void rejectsFirstNameShorterThanThreeCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new UpdateUserProfileRequest("jo", "Tester");

            var violations = validator.validate(request);
            assertTrue(violations.stream().anyMatch(
                    violation -> violation.getPropertyPath()
                            .toString()
                            .equals("firstName")));
        }
    }

    @Test
    void acceptsNamesAtMinimumLength() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new UpdateUserProfileRequest("Joe", "Doe");

            var violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }
    }
}

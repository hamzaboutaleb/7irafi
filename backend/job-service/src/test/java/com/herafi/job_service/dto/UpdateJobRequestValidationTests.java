package com.herafi.job_service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateJobRequestValidationTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void rejectsBlankTitle() {
        Set<ConstraintViolation<UpdateJobRequest>> violations = validator.validate(
                new UpdateJobRequest("", "Description", "Address"));

        assertHasViolation(violations, "title");
    }

    @Test
    void rejectsBlankDescription() {
        Set<ConstraintViolation<UpdateJobRequest>> violations = validator.validate(
                new UpdateJobRequest("Title", " ", "Address"));

        assertHasViolation(violations, "description");
    }

    @Test
    void rejectsOversizedAddressLine() {
        Set<ConstraintViolation<UpdateJobRequest>> violations = validator.validate(
                new UpdateJobRequest("Title", "Description", "a".repeat(201)));

        assertHasViolation(violations, "addressLine");
    }

    @Test
    void acceptsValidRequest() {
        Set<ConstraintViolation<UpdateJobRequest>> violations = validator.validate(
                new UpdateJobRequest("Title", "Description", "Address"));

        assertEquals(0, violations.size());
    }

    private void assertHasViolation(
            Set<ConstraintViolation<UpdateJobRequest>> violations,
            String field) {
        long count = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(field))
                .count();

        assertEquals(1, count);
    }
}

package com.store.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateMeRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsMissingRequiredNames() {
        UpdateMeRequest request = UpdateMeRequest.builder()
                .firstName(" ")
                .lastName("")
                .phoneNumber("")
                .build();

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting("propertyPath")
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("firstName", "lastName");
    }

    @Test
    void allowsOptionalPhoneAndAcceptsValidNames() {
        UpdateMeRequest request = UpdateMeRequest.builder()
                .firstName("Ali")
                .lastName("Seyam")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsOverlongPhoneNumber() {
        UpdateMeRequest request = UpdateMeRequest.builder()
                .firstName("Ali")
                .lastName("Seyam")
                .phoneNumber("1".repeat(33))
                .build();

        assertThat(validator.validate(request))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .containsExactly("phoneNumber");
    }
}

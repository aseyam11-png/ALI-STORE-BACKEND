package com.store.common.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordLengthValidatorTest {

    private final PasswordLengthValidator validator = newValidator(8, 72);

    private static PasswordLengthValidator newValidator(int min, int max) {
        PasswordLengthValidator v = new PasswordLengthValidator();
        v.initialize(new ValidPasswordLength() {
            public int min() { return min; }
            public int max() { return max; }
            public String message() { return ""; }
            public Class<?>[] groups() { return new Class<?>[0]; }
            public Class<? extends jakarta.validation.Payload>[] payload() { return new Class[0]; }
            public Class<? extends java.lang.annotation.Annotation> annotationType() { return ValidPasswordLength.class; }
        });
        return v;
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        // 36 Arabic letters = 72 bytes exactly (2 bytes/char), 36 characters.
        String exactlyAtByteLimit = "ا".repeat(36);
        assertTrue(validator.isValid(exactlyAtByteLimit, null), "36 chars x 2 bytes = 72 bytes should be accepted");

        // One more character pushes it to 74 bytes while still only 37-38 characters -
        // a character-counting validator would accept this; the byte-counting one must not.
        String overByteLimit = "ا".repeat(36) + "A1";
        assertFalse(validator.isValid(overByteLimit, null), "74 bytes must be rejected even at 38 characters");
    }

    @Test
    void plainAsciiBehavesLikeCharacterCount() {
        assertTrue(validator.isValid("Password123", null));
        assertFalse(validator.isValid("Short1", null));
        assertFalse(validator.isValid("A".repeat(73), null));
    }

    @Test
    void nullIsValid_delegatedToNotBlank() {
        assertTrue(validator.isValid(null, null));
    }
}

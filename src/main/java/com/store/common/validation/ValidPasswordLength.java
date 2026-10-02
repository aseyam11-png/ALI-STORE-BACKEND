package com.store.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a password's length in UTF-8 BYTES, not characters. BCrypt (used for password
 * hashing - see SecurityConfig) only hashes the first 72 BYTES of its input. jakarta.validation's
 * built-in @Size counts characters, which is wrong here: a password using multi-byte UTF-8
 * characters (Arabic, emoji, etc.) can exceed BCrypt's 72-byte limit while still being well
 * under a 72-CHARACTER limit, silently truncating and letting two different passwords that
 * share a 72-byte prefix hash identically without the user ever being told.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordLengthValidator.class)
public @interface ValidPasswordLength {

    String message() default "Password must be between {min} and {max} bytes long (UTF-8 encoded)";

    int min() default 8;

    int max() default 72;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

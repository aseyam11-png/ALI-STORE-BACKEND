package com.store.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordLengthValidator implements ConstraintValidator<ValidPasswordLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(ValidPasswordLength constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            // @NotBlank (applied separately on the field) is responsible for null/blank rejection.
            return true;
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= min && byteLength <= max;
    }
}

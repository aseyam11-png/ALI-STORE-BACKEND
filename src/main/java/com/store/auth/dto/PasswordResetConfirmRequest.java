package com.store.auth.dto;

import com.store.common.validation.ValidPasswordLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetConfirmRequest {

    @NotBlank(message = "Verification challenge is required")
    private String challengeId;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits")
    private String code;

    @NotBlank(message = "New password is required")
    @ValidPasswordLength
    private String newPassword;
}

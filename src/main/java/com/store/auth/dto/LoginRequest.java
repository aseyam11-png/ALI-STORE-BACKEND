package com.store.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.store.common.validation.ValidPasswordLength;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email is too long")
    private String email;

    @NotBlank(message = "Password is required")
    @ValidPasswordLength
    private String password;

    // Required only for ROLE_ADMIN in the production profile. The value is
    // verified against ADMIN_GATE_PASSWORD_HASH and is never persisted.
    @Size(max = 256, message = "Administrator gate password is too long")
    private String adminGatePassword;

    // Cloudflare Turnstile token; validated server-side and never persisted.
    @Size(max = 2048, message = "CAPTCHA token is too long")
    private String captchaToken;
}

package com.store.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.ToString;
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
    private String email;

    @NotBlank(message = "Password is required")
    @ToString.Exclude
    private String password;

    // Required only when the authenticated account has ROLE_ADMIN.
    @Size(max = 1024)
    @ToString.Exclude
    private String adminGatePassword;

    // Cloudflare Turnstile response token; validated server-side for administrators.
    @Size(max = 4096)
    @ToString.Exclude
    private String turnstileToken;
}
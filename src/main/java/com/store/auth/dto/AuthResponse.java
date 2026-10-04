package com.store.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    @JsonIgnore
    @ToString.Exclude
    private String token;
    private String email;
    private String role;

    @Builder.Default
    private boolean requiresEmailVerification = false;

    private String challengeId;
    private String maskedEmail;
    private Long expiresInSeconds;
    private com.store.auth.entity.EmailOtpPurpose purpose;
}

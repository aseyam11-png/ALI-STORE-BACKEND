package com.store.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailOtpVerificationRequest {

    @NotBlank(message = "Verification challenge is required")
    private String challengeId;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits")
    private String code;

}

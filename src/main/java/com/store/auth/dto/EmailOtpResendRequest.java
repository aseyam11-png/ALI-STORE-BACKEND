package com.store.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailOtpResendRequest {

    @NotBlank(message = "Verification challenge is required")
    private String challengeId;

}

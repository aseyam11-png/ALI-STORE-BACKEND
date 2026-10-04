package com.store.auth.dto;

import com.store.auth.entity.EmailOtpPurpose;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailOtpChallengeResponse {

    private boolean requiresEmailVerification;
    private String challengeId;
    private String maskedEmail;
    private long expiresInSeconds;
    private EmailOtpPurpose purpose;
}

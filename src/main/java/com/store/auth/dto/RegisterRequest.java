package com.store.auth.dto;

import com.store.common.validation.ValidPasswordLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    // Byte length, not character length - see ValidPasswordLength's javadoc. BCrypt only
    // hashes the first 72 BYTES; a character-count @Size would let a Unicode-heavy password
    // silently exceed that and truncate without the user ever being told.
    @ValidPasswordLength
    private String password;

    private String phoneNumber;
}
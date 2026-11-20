package com.store.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String role;
}

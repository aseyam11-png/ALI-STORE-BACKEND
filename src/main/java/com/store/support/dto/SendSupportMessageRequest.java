package com.store.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendSupportMessageRequest {
    @NotBlank
    @Size(max = 4000)
    private String body;
}

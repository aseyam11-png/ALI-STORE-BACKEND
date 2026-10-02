package com.store.support.dto;

import com.store.support.entity.SupportConversation.SupportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationRequest {
    @NotBlank
    private String subject;

    @NotNull
    private SupportCategory category;
}

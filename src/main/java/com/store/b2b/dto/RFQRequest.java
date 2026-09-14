package com.store.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RFQRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private String notes;

    @NotEmpty(message = "RFQ must contain at least one item")
    private List<RFQItemRequest> items;

    @Data
    public static class RFQItemRequest {
        private Long productId;
        private Integer quantity;
    }
}
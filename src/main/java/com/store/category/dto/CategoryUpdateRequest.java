package com.store.category.dto;

import lombok.Data;

/**
 * Partial update - every field is optional; only non-null fields are applied.
 */
@Data
public class CategoryUpdateRequest {
    private String name;
    private String description;
    private String imageUrl;
    private Long parentId;
    private Boolean active;
}

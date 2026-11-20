package com.store.category.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String slug;

    private String description;

    private String imageUrl;

    private Long parentId;
}

package com.store.category.service;

import com.store.category.dto.CategoryRequest;
import com.store.category.dto.CategoryResponse;
import com.store.category.dto.CategoryUpdateRequest;
import com.store.category.entity.Category;
import com.store.category.repository.CategoryRepository;
import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Category name already exists");
        }

        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + request.getParentId()));
            if (!Boolean.TRUE.equals(parent.getActive())) {
                throw new BusinessException("A category cannot be created under an inactive parent");
            }
        }

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? SlugUtils.slugify(request.getSlug())
                : SlugUtils.uniqueSlugify(name, categoryRepository::existsBySlug);

        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category slug already exists: " + slug);
        }

        return mapToResponse(categoryRepository.save(Category.builder()
                .name(name)
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .active(true)
                .parent(parent)
                .build()));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            categoryRepository.findByName(name)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ConflictException("Category name already exists");
                    });
            category.setName(name);
        }

        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getImageUrl() != null) category.setImageUrl(request.getImageUrl());

        if (request.getActive() != null) {
            if (request.getActive()) {
                if (category.getParent() != null && !Boolean.TRUE.equals(category.getParent().getActive())) {
                    throw new BusinessException("A category cannot be activated while its parent is inactive");
                }
                category.setActive(true);
            } else {
                category.setActive(false);
                deactivateDescendants(category);
            }
        }

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException("A category cannot be its own parent");
            }

            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category not found with id: " + request.getParentId()));

            assertNoCycle(category, parent);
            if (Boolean.TRUE.equals(category.getActive()) && !Boolean.TRUE.equals(parent.getActive())) {
                throw new BusinessException("An active category cannot have an inactive parent");
            }
            category.setParent(parent);
        }

        return mapToResponse(categoryRepository.save(category));
    }

    private void deactivateDescendants(Category category) {
        for (Category child : categoryRepository.findByParentId(category.getId())) {
            if (Boolean.TRUE.equals(child.getActive())) {
                child.setActive(false);
                categoryRepository.save(child);
            }
            deactivateDescendants(child);
        }
    }

    private void assertNoCycle(Category category, Category proposedParent) {
        Category current = proposedParent;
        while (current != null) {
            if (current.getId().equals(category.getId())) {
                throw new BusinessException(
                        "Cannot set parent: this would create a circular category hierarchy");
            }
            current = current.getParent();
        }
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        CategoryUpdateRequest request = new CategoryUpdateRequest();
        request.setActive(active);
        return updateCategory(id, request);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .filter(this::isEffectivelyActive)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .filter(this::isEffectivelyActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .filter(this::isEffectivelyActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with slug: " + slug));
        return mapToResponse(category);
    }

    private boolean isEffectivelyActive(Category category) {
        Category current = category;
        while (current != null) {
            if (!Boolean.TRUE.equals(current.getActive())) return false;
            current = current.getParent();
        }
        return true;
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.getActive())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .build();
    }
}

package com.store.brand.service;

import com.store.brand.dto.BrandRequest;
import com.store.brand.dto.BrandResponse;
import com.store.brand.entity.Brand;
import com.store.brand.repository.BrandRepository;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public BrandResponse getBrandById(Long id) {
        return mapToResponse(brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id)));
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String name = request.getName().trim();
        if (brandRepository.existsByName(name)) {
            throw new ConflictException("Brand name already exists");
        }

        return mapToResponse(brandRepository.save(Brand.builder()
                .name(name)
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .build()));
    }

    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));

        String name = request.getName().trim();
        brandRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Brand name already exists");
                });

        brand.setName(name);
        brand.setDescription(request.getDescription());
        brand.setLogoUrl(request.getLogoUrl());
        return mapToResponse(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new ResourceNotFoundException("Brand not found with id: " + id);
        }
        brandRepository.deleteById(id);
    }

    private BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .description(brand.getDescription())
                .logoUrl(brand.getLogoUrl())
                .build();
    }
}

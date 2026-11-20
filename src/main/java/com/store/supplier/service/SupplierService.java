package com.store.supplier.service;

import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.response.PageResponse;
import com.store.supplier.dto.SupplierRequest;
import com.store.supplier.dto.SupplierResponse;
import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import com.store.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public PageResponse<SupplierResponse> getAll(Pageable pageable) {
        Page<SupplierResponse> page = supplierRepository.findAll(pageable).map(this::map);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public SupplierResponse getById(Long id) {
        return map(getEntity(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String code = request.getExternalCode().trim();
        if (supplierRepository.existsByExternalCode(code)) {
            throw new ConflictException("Supplier external code already exists");
        }

        Supplier supplier = Supplier.builder()
                .externalCode(code)
                .name(request.getName().trim())
                .apiBaseUrl(normalizeUrl(request.getApiBaseUrl()))
                .fulfillmentEnabled(request.getFulfillmentEnabled() != null ? request.getFulfillmentEnabled() : true)
                .inventorySyncEnabled(request.getInventorySyncEnabled() != null ? request.getInventorySyncEnabled() : true)
                .status(SupplierStatus.ACTIVE)
                .build();

        return map(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = getEntity(id);
        String code = request.getExternalCode().trim();

        supplierRepository.findByExternalCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Supplier external code already exists");
                });

        supplier.setExternalCode(code);
        supplier.setName(request.getName().trim());
        supplier.setApiBaseUrl(normalizeUrl(request.getApiBaseUrl()));
        if (request.getFulfillmentEnabled() != null) {
            supplier.setFulfillmentEnabled(request.getFulfillmentEnabled());
        }
        if (request.getInventorySyncEnabled() != null) {
            supplier.setInventorySyncEnabled(request.getInventorySyncEnabled());
        }

        return map(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse updateStatus(Long id, SupplierStatus status) {
        Supplier supplier = getEntity(id);
        supplier.setStatus(status);
        return map(supplierRepository.save(supplier));
    }

    private Supplier getEntity(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + id));
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("/+$", "");
    }

    private SupplierResponse map(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .externalCode(supplier.getExternalCode())
                .name(supplier.getName())
                .apiBaseUrl(supplier.getApiBaseUrl())
                .status(supplier.getStatus())
                .fulfillmentEnabled(supplier.getFulfillmentEnabled())
                .inventorySyncEnabled(supplier.getInventorySyncEnabled())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}

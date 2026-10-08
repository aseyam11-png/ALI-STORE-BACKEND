package com.store.shipping.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ConflictException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.shipping.dto.ShippingMethodRequest;
import com.store.shipping.dto.ShippingMethodResponse;
import com.store.shipping.entity.ShippingMethod;
import com.store.shipping.repository.ShippingMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShippingMethodService {

    private final ShippingMethodRepository repository;

    @Value("${app.shipping.require-method:false}")
    private boolean requireShippingMethod;

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> getPublicMethods(String country, String currency) {
        String normalizedCountry = normalizeCountry(country);
        String normalizedCurrency = normalizeOptionalCurrency(currency);
        List<ShippingMethod> methods = normalizedCurrency == null
                ? repository.findPublicMethods(normalizedCountry)
                : repository.findPublicMethodsByCurrency(normalizedCountry, normalizedCurrency);
        return methods.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public ShippingMethod resolveForOrder(String code, String country, String currency) {
        String normalizedCountry = normalizeCountry(country);
        String normalizedCurrency = normalizeCurrency(currency);
        List<ShippingMethod> methods = normalizedCurrency == null
                ? repository.findPublicMethods(normalizedCountry)
                : repository.findPublicMethodsByCurrency(normalizedCountry, normalizedCurrency);

        if (code == null || code.isBlank()) {
            if (requireShippingMethod || !methods.isEmpty()) {
                throw new BusinessException("Shipping method is required");
            }
            return null;
        }

        String normalizedCode = code.trim();
        return methods.stream()
                .filter(m -> m.getCode().equalsIgnoreCase(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipping method is not available for this destination"));
    }

    @Transactional(readOnly = true)
    public List<ShippingMethodResponse> getAllAdmin() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(ShippingMethod::getPriority, Comparator.reverseOrder())
                        .thenComparing(ShippingMethod::getId))
                .map(this::map)
                .toList();
    }

    @Transactional
    public ShippingMethodResponse create(ShippingMethodRequest request) {
        validate(request);
        String code = normalizeCode(request.getCode());
        String country = normalizeCountry(request.getCountryCode());
        if (repository.existsByCodeAndCountryCode(code, country)) {
            throw new ConflictException("Shipping method code already exists for this destination");
        }

        ShippingMethod method = ShippingMethod.builder()
                .code(code)
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .price(money(request.getPrice()))
                .currency(normalizeCurrency(request.getCurrency()))
                .estimatedMinDays(request.getEstimatedMinDays())
                .estimatedMaxDays(request.getEstimatedMaxDays())
                .countryCode(country)
                .active(request.getActive() == null || request.getActive())
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .build();

        return map(repository.save(method));
    }

    @Transactional
    public ShippingMethodResponse update(Long id, ShippingMethodRequest request) {
        validate(request);
        ShippingMethod method = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping method not found"));

        String code = normalizeCode(request.getCode());
        String country = normalizeCountry(request.getCountryCode());
        repository.findAll().stream()
                .filter(existing -> !existing.getId().equals(id))
                .filter(existing -> existing.getCode().equalsIgnoreCase(code)
                        && Objects.equals(existing.getCountryCode(), country))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ConflictException("Shipping method code already exists for this destination");
                });

        method.setCode(code);
        method.setName(request.getName().trim());
        method.setDescription(blankToNull(request.getDescription()));
        method.setPrice(money(request.getPrice()));
        method.setCurrency(normalizeCurrency(request.getCurrency()));
        method.setEstimatedMinDays(request.getEstimatedMinDays());
        method.setEstimatedMaxDays(request.getEstimatedMaxDays());
        method.setCountryCode(country);
        method.setActive(request.getActive() == null || request.getActive());
        method.setPriority(request.getPriority() == null ? 0 : request.getPriority());

        return map(repository.save(method));
    }

    @Transactional
    public void delete(Long id) {
        ShippingMethod method = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping method not found"));
        method.setActive(false);
        repository.save(method);
    }

    private void validate(ShippingMethodRequest request) {
        if (request.getPrice() == null || request.getPrice().signum() < 0) {
            throw new BusinessException("Shipping price cannot be negative");
        }
        if (request.getEstimatedMinDays() == null || request.getEstimatedMaxDays() == null
                || request.getEstimatedMinDays() < 0
                || request.getEstimatedMaxDays() < request.getEstimatedMinDays()) {
            throw new BusinessException("Shipping delivery window is invalid");
        }
        normalizeCurrency(request.getCurrency());
    }

    private ShippingMethodResponse map(ShippingMethod method) {
        return ShippingMethodResponse.builder()
                .id(method.getId()).code(method.getCode()).name(method.getName())
                .description(method.getDescription()).price(method.getPrice()).currency(method.getCurrency())
                .estimatedMinDays(method.getEstimatedMinDays()).estimatedMaxDays(method.getEstimatedMaxDays())
                .countryCode(method.getCountryCode()).active(method.getActive()).priority(method.getPriority())
                .createdAt(method.getCreatedAt()).updatedAt(method.getUpdatedAt()).build();
    }

    private String normalizeCountry(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Shipping currency is required");
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BusinessException("Shipping currency must be a 3-letter ISO code");
        }
        return normalized;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
package com.store.visualsearch.service;

import com.store.product.entity.Product;
import com.store.product.entity.ProductImage;
import com.store.product.enums.ProductStatus;
import com.store.product.repository.ProductRepository;
import com.store.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisualSearchCandidateService {
    private final ProductRepository productRepository;
    private final VisualSearchProperties properties;

    @Transactional(readOnly = true)
    public List<Candidate> loadCandidates() {
        Specification<Product> spec = Specification.where(ProductSpecifications.hasStatus(ProductStatus.ACTIVE))
                .and(ProductSpecifications.hasActiveCategory())
                .and(ProductSpecifications.hasActiveSupplier());
        List<Product> products = productRepository.findAll(
                spec, PageRequest.of(0, properties.maxCandidates(), Sort.by(Sort.Direction.ASC, "id")))
                .getContent();

        return products.stream().map(product -> {
            ProductImage imageRef = product.getImages().stream()
                    .filter(ProductImage::getIsPrimary).findFirst()
                    .orElseGet(() -> product.getImages().stream()
                            .min(Comparator.comparing(ProductImage::getSortOrder)).orElse(null));
            return new Candidate(
                    product.getId(),
                    product.getName(),
                    imageRef == null ? null : imageRef.getUrl(),
                    product.getPrice(),
                    product.getCurrency(),
                    product.getBrand() == null ? "" : product.getBrand().getName(),
                    product.getCategory().getName());
        }).toList();
    }

    public record Candidate(
            Long productId,
            String productName,
            String imageUrl,
            BigDecimal price,
            String currency,
            String brand,
            String category) {
    }
}

package com.store.product.repository;

import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.PublicAvailability;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> hasActiveCategory() {
        return (root, query, cb) -> cb.isTrue(root.get("category").get("active"));
    }

    public static Specification<Product> hasActiveSupplier() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("supplier").get("status"), com.store.supplier.entity.SupplierStatus.ACTIVE),
                cb.isTrue(root.get("supplier").get("fulfillmentEnabled"))
        );
    }

    public static Specification<Product> inCategorySlug(String slug) {
        return (root, query, cb) -> cb.equal(root.get("category").get("slug"), slug);
    }

    public static Specification<Product> hasBrandName(String brandName) {
        return (root, query, cb) -> cb.equal(
                cb.lower(root.get("brand").get("name")),
                brandName.toLowerCase());
    }

    public static Specification<Product> statusIn(ProductStatus... statuses) {
        return (root, query, cb) -> root.get("status").in((Object[]) statuses);
    }

    public static Specification<Product> nameOrSkuContains(String q) {
        return matchesQuery(q);
    }

    /**
     * Customer search is token-aware and database-backed. Every meaningful token
     * must appear in at least one public catalog field. This allows queries such
     * as "wireless mouse" to match across name, description, brand and category
     * without exposing supplier metadata or loading the catalog into memory.
     */
    public static Specification<Product> matchesQuery(String q) {
        String normalized = q == null ? "" : q.trim().toLowerCase();
        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .limit(8)
                .toList();

        return (root, query, cb) -> {
            if (tokens.isEmpty()) return cb.conjunction();

            var brand = root.join("brand", JoinType.LEFT);
            var category = root.join("category", JoinType.LEFT);
            var allTokens = cb.conjunction();

            for (String token : tokens) {
                String like = "%" + token + "%";
                allTokens = cb.and(allTokens, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(brand.get("name")), like),
                        cb.like(cb.lower(category.get("name")), like),
                        cb.like(cb.lower(category.get("slug")), like)
                ));
            }

            query.distinct(true);
            return allTokens;
        };
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> hasBrand(Long brandId) {
        return (root, query, cb) -> cb.equal(root.get("brand").get("id"), brandId);
    }

    public static Specification<Product> priceGreaterThanOrEqual(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceLessThanOrEqual(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> hasAvailability(PublicAvailability availability) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var inventoryRoot = subquery.from(Inventory.class);
            subquery.select(inventoryRoot.get("id"))
                    .where(
                            cb.equal(inventoryRoot.get("product"), root),
                            cb.isNotNull(inventoryRoot.get("lastSyncedAt")),
                            availability == PublicAvailability.IN_STOCK
                                    ? cb.greaterThan(cb.diff(inventoryRoot.get("quantity"), inventoryRoot.get("reservedQuantity")), 0)
                                    : cb.lessThanOrEqualTo(cb.diff(inventoryRoot.get("quantity"), inventoryRoot.get("reservedQuantity")), 0)
                    );
            return cb.exists(subquery);
        };
    }
}

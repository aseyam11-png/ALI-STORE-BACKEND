package com.store.product.repository;

import com.store.inventory.entity.Inventory;
import com.store.inventory.enums.PublicAvailability;
import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

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

    public static Specification<Product> statusIn(ProductStatus... statuses) {
        return (root, query, cb) -> root.get("status").in((Object[]) statuses);
    }

    public static Specification<Product> nameOrSkuContains(String q) {
        String like = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("sku")), like)
        );
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
                            availability == PublicAvailability.IN_STOCK
                                    ? cb.greaterThan(cb.diff(inventoryRoot.get("quantity"), inventoryRoot.get("reservedQuantity")), 0)
                                    : cb.lessThanOrEqualTo(cb.diff(inventoryRoot.get("quantity"), inventoryRoot.get("reservedQuantity")), 0)
                    );
            return cb.exists(subquery);
        };
    }
}

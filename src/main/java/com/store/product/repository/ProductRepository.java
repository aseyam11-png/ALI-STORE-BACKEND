package com.store.product.repository;

import com.store.product.entity.Product;
import com.store.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
    Optional<Product> findBySku(String sku);
    Optional<Product> findBySlug(String slug);
    Optional<Product> findBySlugAndStatusIn(String slug, java.util.List<ProductStatus> statuses);
    Optional<Product> findBySupplierIdAndSupplierSku(Long supplierId, String supplierSku);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);
}

package com.store.product.repository;

import com.store.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdAndActiveTrueOrderByIdAsc(Long productId);
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
    Optional<ProductVariant> findByIdAndProductId(Long id, Long productId);
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id = :id")
    Optional<ProductVariant> findWithProductForUpdate(Long id);
}
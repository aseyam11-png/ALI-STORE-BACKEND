package com.store.cart.repository;


import com.store.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("select ci from CartItem ci where ci.cart.id = :cartId and ci.product.id = :productId and " +
            "(:variantId is null and ci.variant is null or ci.variant.id = :variantId)")
    Optional<CartItem> findByCartIdAndProductIdAndVariantId(
            @org.springframework.data.repository.query.Param("cartId") Long cartId,
            @org.springframework.data.repository.query.Param("productId") Long productId,
            @org.springframework.data.repository.query.Param("variantId") Long variantId);

    @Query("select ci from CartItem ci join fetch ci.product left join fetch ci.variant where ci.cart.id = :cartId")
    List<CartItem> findByCartId(Long cartId);

    void deleteByCartId(Long cartId);
}
package com.store.cart.repository;


import com.store.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Query("select ci from CartItem ci join fetch ci.product p join fetch p.category left join fetch p.supplier where ci.cart.id = :cartId")
    List<CartItem> findByCartId(Long cartId);

    void deleteByCartId(Long cartId);
}
package com.store.cart.repository;

import com.store.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    // Row-locks the cart for the duration of the checkout transaction so two concurrent
    // checkout requests for the same user cannot both read the same cart items and each
    // create a separate order from what the customer intended as a single purchase.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cart> findWithLockByUserId(Long userId);
}
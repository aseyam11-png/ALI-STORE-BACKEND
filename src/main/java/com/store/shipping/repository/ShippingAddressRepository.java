package com.store.shipping.repository;

import com.store.shipping.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
    List<ShippingAddress> findByUserId(Long userId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ShippingAddress a set a.isDefault = false where a.user.id = :userId")
    void clearDefaultForUser(@org.springframework.data.repository.query.Param("userId") Long userId);
}
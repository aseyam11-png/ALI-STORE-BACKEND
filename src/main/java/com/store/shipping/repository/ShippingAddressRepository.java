package com.store.shipping.repository;

import com.store.shipping.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {

    List<ShippingAddress> findByUserIdOrderByIsDefaultDescIdAsc(Long userId);

    Optional<ShippingAddress> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update ShippingAddress a set a.isDefault = false where a.user.id = :userId")
    int clearDefaultForUser(@Param("userId") Long userId);
}
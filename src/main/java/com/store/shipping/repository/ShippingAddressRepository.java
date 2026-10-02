package com.store.shipping.repository;

import com.store.shipping.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
    List<ShippingAddress> findByUserId(Long userId);

    Optional<ShippingAddress> findFirstByUserIdOrderByIdAsc(Long userId);

    @Modifying
    @Query("UPDATE ShippingAddress s SET s.isDefault = false WHERE s.user.id = :userId")
    int clearDefaultForUser(Long userId);
}

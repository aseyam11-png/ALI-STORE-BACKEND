package com.store.coupon.repository;

import com.store.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    boolean existsByCode(String code);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed + 1 " +
            "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    int tryRedeem(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed - 1 " +
            "WHERE c.id = :id AND c.timesUsed > 0")
    int tryRelease(Long id);
}

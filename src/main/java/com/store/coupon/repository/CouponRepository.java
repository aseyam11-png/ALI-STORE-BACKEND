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

    /**
     * Atomically consumes one redemption, in a single UPDATE ... WHERE statement rather
     * than a read-then-write. This is what actually makes concurrent redemption safe: two
     * transactions racing to redeem the last remaining use both issue this UPDATE against
     * the same row; the database's own row-level locking serializes them (the second one
     * blocks until the first commits, then re-evaluates the WHERE clause against the
     * now-incremented times_used), so at most one of them can match the WHERE condition
     * and actually increment the counter. A naive "read timesUsed, check it, then save"
     * approach would NOT be safe here even with @Version optimistic locking unless paired
     * with a retry loop - this avoids needing one. Returns the number of rows updated: 1 if
     * the redemption was accepted, 0 if the coupon has hit its usage limit.
     */
    // clearAutomatically: without it, a Coupon instance already cached in this transaction's
    // persistence context (e.g. from validateAndGetCoupon's earlier findByCode) would keep
    // showing the pre-redemption timesUsed on any subsequent read in the same transaction,
    // even though the UPDATE below did take effect in the database.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed + 1 " +
            "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    int tryRedeem(Long id);
}
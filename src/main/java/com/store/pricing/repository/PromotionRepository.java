package com.store.pricing.repository;

import com.store.pricing.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    @Query("""select p from Promotion p
        join fetch p.product
        where p.active = true
          and p.startsAt <= :now
          and p.endsAt > :now
        order by p.priority desc, p.endsAt asc, p.id asc""")
    List<Promotion> findActive(@Param("now") LocalDateTime now);

    @Query("""select p from Promotion p
        where p.product.id = :productId
          and p.active = true
          and p.startsAt <= :now
          and p.endsAt > :now
        order by p.priority desc, p.endsAt asc, p.id asc""")
    List<Promotion> findActiveForProduct(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
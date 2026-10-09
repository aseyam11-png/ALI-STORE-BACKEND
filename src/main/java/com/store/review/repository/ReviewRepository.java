package com.store.review.repository;

import com.store.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductId(Long productId);
    List<Review> findByUserId(Long userId);
    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @Query(value = """
            SELECT
                r.product_id AS "productId",
                COALESCE(AVG(r.rating), 0) AS "averageRating",
                COUNT(*) AS "reviewCount",
                COALESCE(SUM(CASE WHEN r.rating = 1 THEN 1 ELSE 0 END), 0) AS "oneStar",
                COALESCE(SUM(CASE WHEN r.rating = 2 THEN 1 ELSE 0 END), 0) AS "twoStar",
                COALESCE(SUM(CASE WHEN r.rating = 3 THEN 1 ELSE 0 END), 0) AS "threeStar",
                COALESCE(SUM(CASE WHEN r.rating = 4 THEN 1 ELSE 0 END), 0) AS "fourStar",
                COALESCE(SUM(CASE WHEN r.rating = 5 THEN 1 ELSE 0 END), 0) AS "fiveStar"
            FROM reviews r
            WHERE r.product_id IN (:productIds)
            GROUP BY r.product_id
            """, nativeQuery = true)
    List<ProductRatingSummary> findRatingSummaries(@Param("productIds") Collection<Long> productIds);
}
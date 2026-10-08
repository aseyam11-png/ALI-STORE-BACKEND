package com.store.review.repository;

import java.math.BigDecimal;

public interface ProductRatingSummary {
    Long getProductId();
    BigDecimal getAverageRating();
    Long getReviewCount();
    Long getOneStar();
    Long getTwoStar();
    Long getThreeStar();
    Long getFourStar();
    Long getFiveStar();
}
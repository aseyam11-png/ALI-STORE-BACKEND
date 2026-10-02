package com.store.pricing.repository;

import com.store.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    List<PricingRule> findAllByOrderByPriorityDescIdAsc();
    List<PricingRule> findByActiveTrueAndProductIdOrderByPriorityDescIdAsc(Long productId);
    List<PricingRule> findByActiveTrueAndCategoryIdOrderByPriorityDescIdAsc(Long categoryId);
}
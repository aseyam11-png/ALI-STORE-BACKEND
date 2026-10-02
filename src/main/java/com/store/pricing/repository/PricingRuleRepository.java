package com.store.pricing.repository;

import com.store.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    List<PricingRule> findByActiveTrueOrderByPriorityDescIdAsc();
}

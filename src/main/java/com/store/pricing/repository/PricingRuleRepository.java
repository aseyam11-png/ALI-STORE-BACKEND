package com.store.pricing.repository;

import com.store.pricing.entity.PricingRule;
import com.store.pricing.entity.PricingRuleScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    @Query("""
            select r from PricingRule r
            left join fetch r.category
            left join fetch r.product
            where r.active = true
              and (
                r.scope = com.store.pricing.entity.PricingRuleScope.GLOBAL
                or (r.scope = com.store.pricing.entity.PricingRuleScope.CATEGORY and r.category.id = :categoryId)
                or (r.scope = com.store.pricing.entity.PricingRuleScope.PRODUCT and r.product.id = :productId)
              )
            order by
              case
                when r.scope = com.store.pricing.entity.PricingRuleScope.PRODUCT then 3
                when r.scope = com.store.pricing.entity.PricingRuleScope.CATEGORY then 2
                else 1
              end desc,
              r.priority desc,
              r.id desc
            """)
    List<PricingRule> findEffectiveRules(Long categoryId, Long productId);
}

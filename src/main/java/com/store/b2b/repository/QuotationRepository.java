package com.store.b2b.repository;

import com.store.b2b.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByRfqId(Long rfqId);
    List<Quotation> findBySellerId(Long sellerId);
}
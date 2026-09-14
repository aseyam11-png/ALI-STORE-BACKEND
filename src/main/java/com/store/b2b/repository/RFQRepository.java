package com.store.b2b.repository;

import com.store.b2b.entity.RFQ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RFQRepository extends JpaRepository<RFQ, Long> {
    List<RFQ> findByUserId(Long userId);
}
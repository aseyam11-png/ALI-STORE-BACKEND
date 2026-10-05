package com.store.supplier.repository;

import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByExternalCode(String externalCode);
    Optional<Supplier> findByExternalCode(String externalCode);
    List<Supplier> findByStatusAndInventorySyncEnabledTrue(SupplierStatus status);
    Page<Supplier> findAll(Pageable pageable);
}

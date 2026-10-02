package com.store.supplier.repository;

import com.store.supplier.entity.Supplier;
import com.store.supplier.entity.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByExternalCode(String externalCode);
    List<Supplier> findByStatus(SupplierStatus status);
    boolean existsByExternalCode(String externalCode);
}

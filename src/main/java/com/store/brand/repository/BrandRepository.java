package com.store.brand.repository;

import com.store.brand.entity.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);

    @Query("""
            select b from Brand b
            where lower(b.name) like lower(concat('%', :query, '%'))
            order by b.name asc
            """)
    Page<Brand> searchByName(@Param("query") String query, Pageable pageable);
}

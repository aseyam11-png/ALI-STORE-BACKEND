package com.store.category.repository;

import com.store.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    Optional<Category> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);

    @Query("""
            select c from Category c
            where c.active = true
              and (
                  lower(c.name) like lower(concat('%', :query, '%'))
                  or lower(c.slug) like lower(concat('%', :query, '%'))
                  or lower(coalesce(c.description, '')) like lower(concat('%', :query, '%'))
              )
            order by c.name asc
            """)
    Page<Category> searchActive(@Param("query") String query, Pageable pageable);
}

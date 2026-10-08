package com.store.shipping.repository;

import com.store.shipping.entity.ShippingMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {
    Optional<ShippingMethod> findByCodeAndCountryCode(String code, String countryCode);
    @Query("""select m from ShippingMethod m where m.active = true
        and (m.countryCode is null or upper(m.countryCode) = upper(:country))
        order by m.priority desc, m.id asc""")
    List<ShippingMethod> findPublicMethods(@Param("country") String country);
    @Query("""select m from ShippingMethod m where m.active = true
        and m.currency = :currency
        and (m.countryCode is null or upper(m.countryCode) = upper(:country))
        order by m.priority desc, m.id asc""")
    List<ShippingMethod> findPublicMethodsByCurrency(@Param("country") String country, @Param("currency") String currency);
    boolean existsByCodeAndCountryCode(String code, String countryCode);
}
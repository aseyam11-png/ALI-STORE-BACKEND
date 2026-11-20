package com.store.auth.repository;

import com.store.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {
    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);
}

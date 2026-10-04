package com.store.auth.repository;

import com.store.auth.entity.EmailOtpChallenge;
import com.store.auth.entity.EmailOtpPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from EmailOtpChallenge c
            join fetch c.user
            where c.challengeId = :challengeId
            """)
    Optional<EmailOtpChallenge> findForVerification(@Param("challengeId") UUID challengeId);

    Optional<EmailOtpChallenge> findTopByUserIdAndPurposeAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
            Long userId, EmailOtpPurpose purpose);

    @Modifying
    @Query("""
            update EmailOtpChallenge c
            set c.revokedAt = :revokedAt
            where c.user.id = :userId
              and c.purpose = :purpose
              and c.consumedAt is null
              and c.revokedAt is null
            """)
    int revokeActiveChallenges(
            @Param("userId") Long userId,
            @Param("purpose") EmailOtpPurpose purpose,
            @Param("revokedAt") Instant revokedAt
    );
}

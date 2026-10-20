package com.store.user.repository;

import com.store.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.failedLoginAttempts = coalesce(u.failedLoginAttempts, 0) + 1,
                   u.lockedUntil = case
                       when coalesce(u.failedLoginAttempts, 0) + 1 >= :maxAttempts
                           then :lockUntil
                       else u.lockedUntil
                   end
             where lower(u.email) = lower(:email)
               and u.isEnabled = true
               and (u.lockedUntil is null or u.lockedUntil <= :now)
            """)
    int recordFailedLogin(
            @Param("email") String email,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") LocalDateTime now,
            @Param("lockUntil") LocalDateTime lockUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.failedLoginAttempts = 0,
                   u.lockedUntil = null
             where lower(u.email) = lower(:email)
            """)
    int resetFailedLogin(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from User u
            join fetch u.role r
            where r.name = 'ROLE_ADMIN'
              and u.isEnabled = true
            order by u.id
            """)
    List<User> findEnabledAdminsForUpdate();

    List<User> findByRoleNameAndIsEnabledTrue(String roleName);
}
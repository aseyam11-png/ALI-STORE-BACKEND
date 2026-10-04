package com.store.user.repository;

import com.store.user.entity.AuthProvider;
import com.store.user.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    boolean existsByUserIdAndProvider(Long userId, AuthProvider provider);
}

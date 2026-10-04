package com.store.auth.service;

import com.store.common.exception.BusinessException;
import com.store.common.exception.ResourceNotFoundException;
import com.store.user.entity.AuthProvider;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.entity.UserIdentity;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserIdentityRepository;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleUserProvisioningService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public User provision(OidcUser oidcUser) {
        String subject = requireNonBlank(oidcUser.getSubject(), "Google subject is missing");
        String email = requireNonBlank(oidcUser.getEmail(), "Google email is missing")
                .trim().toLowerCase(Locale.ROOT);

        Object verifiedClaim = oidcUser.getClaims().get("email_verified");
        if (!Boolean.TRUE.equals(verifiedClaim)
                && !"true".equalsIgnoreCase(String.valueOf(verifiedClaim))) {
            throw new BusinessException("Google account email is not verified");
        }

        UserIdentity identity = userIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)
                .orElse(null);

        if (identity != null) {
            User existing = identity.getUser();
            if (!Boolean.TRUE.equals(existing.getIsEnabled())) {
                throw new BusinessException("User account is disabled");
            }
            existing.setEmailVerified(true);
            userRepository.save(existing);
            identity.setProviderEmail(email);
            userIdentityRepository.save(identity);
            return existing;
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            Role role = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new ResourceNotFoundException("Default role not found"));

            user = User.builder()
                    .firstName(defaultName(oidcUser.getGivenName(), "Google"))
                    .lastName(defaultName(oidcUser.getFamilyName(), "User"))
                    .email(email)
                    .password(passwordEncoder.encode(generateRandomPassword()))
                    .role(role)
                    .isEnabled(true)
                    .emailVerified(true)
                    .build();

            user = userRepository.save(user);
        }

        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new BusinessException("User account is disabled");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        if (userIdentityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
            throw new BusinessException(
                    "A different Google identity is already linked to this account");
        }

        userIdentityRepository.save(UserIdentity.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerSubject(subject)
                .providerEmail(email)
                .build());

        return user;
    }

    private String generateRandomPassword() {
        return UUID.randomUUID().toString()
                + UUID.randomUUID().toString()
                + Long.toHexString(secureRandom.nextLong());
    }

    private String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value;
    }
}

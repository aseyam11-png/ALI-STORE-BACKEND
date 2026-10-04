package com.store.auth;

import com.store.auth.service.GoogleUserProvisioningService;
import com.store.user.entity.AuthProvider;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserIdentityRepository;
import com.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleUserProvisioningServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserIdentityRepository identityRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OidcUser oidcUser;

    @Test
    void verifiedGoogleIdentity_createsUserAndIdentity() {
        GoogleUserProvisioningService service =
                new GoogleUserProvisioningService(
                        userRepository, identityRepository, roleRepository, passwordEncoder);

        when(oidcUser.getSubject()).thenReturn("google-sub-123");
        when(oidcUser.getEmail()).thenReturn("USER@EXAMPLE.COM");
        when(oidcUser.getClaims()).thenReturn(Map.of("email_verified", true));
        when(oidcUser.getGivenName()).thenReturn("Ali");
        when(oidcUser.getFamilyName()).thenReturn("Store");
        when(identityRepository.findByProviderAndProviderSubject(
                AuthProvider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER"))
                .thenReturn(Optional.of(Role.builder().name("ROLE_USER").build()));
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        User saved = service.provision(oidcUser);

        assertEquals("user@example.com", saved.getEmail());
        assertEquals("Ali", saved.getFirstName());
        assertEquals("Store", saved.getLastName());
        assertEquals("bcrypt-hash", saved.getPassword());
        verify(identityRepository).save(argThat(identity ->
                identity.getProvider() == AuthProvider.GOOGLE
                        && identity.getProviderSubject().equals("google-sub-123")
                        && identity.getProviderEmail().equals("user@example.com")
                        && identity.getUser() == saved));
    }

    @Test
    void unverifiedGoogleEmail_isRejected() {
        GoogleUserProvisioningService service =
                new GoogleUserProvisioningService(
                        userRepository, identityRepository, roleRepository, passwordEncoder);

        when(oidcUser.getSubject()).thenReturn("google-sub-123");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getClaims()).thenReturn(Map.of("email_verified", false));

        assertThrows(
                com.store.common.exception.BusinessException.class,
                () -> service.provision(oidcUser));
        verifyNoInteractions(userRepository, roleRepository, passwordEncoder);
    }
}

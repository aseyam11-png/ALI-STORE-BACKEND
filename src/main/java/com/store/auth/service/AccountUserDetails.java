package com.store.auth.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Store-specific principal carrying the account version used to invalidate existing
 * JWT sessions after a password reset/change.
 */
public final class AccountUserDetails extends User implements AccountVersionAware {

    private final long authVersion;

    public AccountUserDetails(
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities,
            long authVersion) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.authVersion = Math.max(0L, authVersion);
    }

    @Override
    public long getAuthVersion() {
        return authVersion;
    }
}

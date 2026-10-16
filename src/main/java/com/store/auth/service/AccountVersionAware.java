package com.store.auth.service;

/**
 * Exposes the database-backed authentication version embedded in session JWTs.
 * Incrementing the version invalidates every JWT issued for an older version.
 */
public interface AccountVersionAware {
    long getAuthVersion();
}

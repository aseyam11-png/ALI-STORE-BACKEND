package com.store.auth.service;

/**
 * Server-side verification boundary for Cloudflare Turnstile.
 * Keeping this separate makes administrator-gate decisions testable without
 * calling the external provider from unit tests.
 */
public interface TurnstileVerifier {

    boolean verify(String token, String clientIp, String secret, String expectedHostname);
}

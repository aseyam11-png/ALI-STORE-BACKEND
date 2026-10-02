package com.store.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context, mocked HttpServletRequest) for the
 * trusted-proxy-aware client IP resolution backing rate limiting.
 */
class ClientIpResolverTest {

    @Test
    void withNoTrustedProxiesConfigured_alwaysUsesRemoteAddr_ignoringForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        // Default posture: a client claiming any X-Forwarded-For must never override
        // the real TCP peer address unless a trusted proxy has been explicitly configured.
        assertEquals("198.51.100.5", resolver.resolve(request));
    }

    @Test
    void whenImmediatePeerIsTrustedProxy_usesFirstForwardedForEntry() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.5,10.0.0.6");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.5");

        assertEquals("203.0.113.9", resolver.resolve(request));
    }

    @Test
    void whenImmediatePeerIsNotAConfiguredTrustedProxy_ignoresForwardedForEvenThoughListIsNonEmpty() {
        // A request that bypasses the known proxy and hits the app directly must not
        // be able to forge X-Forwarded-For to dodge or misdirect the rate limit.
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.5");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("198.51.100.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertEquals("198.51.100.5", resolver.resolve(request));
    }

    @Test
    void whenTrustedProxySendsNoForwardedForHeader_fallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.5");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        assertEquals("10.0.0.5", resolver.resolve(request));
    }
}

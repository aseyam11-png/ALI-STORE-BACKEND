package com.store.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the client IP a security decision (currently: auth rate limiting) should be
 * keyed on, without blindly trusting X-Forwarded-For.
 *
 * Default behavior (app.trusted-proxies unset/empty): always use request.getRemoteAddr()
 * as-is, ignoring any X-Forwarded-For header entirely. This is the safe default - a client
 * can put anything it wants in X-Forwarded-For, so trusting it unconditionally would let an
 * attacker either spoof past the rate limit (claim a fresh IP every request) or frame another
 * client (claim their IP to get them blocked).
 *
 * Opt-in behavior (app.trusted-proxies set to the known, deployment-specific proxy IP(s)):
 * X-Forwarded-For is only consulted when request.getRemoteAddr() - the IMMEDIATE TCP peer -
 * is itself one of the configured trusted proxies. In that case the first entry in the header
 * (the original client, assuming a single trusted hop that sets rather than blindly appends)
 * is used. A request arriving directly from a non-trusted peer never has its header trusted,
 * even if app.trusted-proxies is configured - so someone bypassing the proxy entirely cannot
 * forge an X-Forwarded-For to dodge the limit either.
 *
 * This is a narrower, purpose-specific, unit-testable alternative to Spring's blanket
 * `server.forward-headers-strategy: framework` (which trusts X-Forwarded-* unconditionally
 * for the whole request, with no allowlist) - deliberately not reused here.
 */
@Component
public class ClientIpResolver {

    private final String trustedProxiesRaw;

    // Constructor injection (rather than field @Value injection) so this class stays
    // trivially instantiable in a plain unit test without a Spring context.
    public ClientIpResolver(@Value("${app.trusted-proxies:}") String trustedProxiesRaw) {
        this.trustedProxiesRaw = trustedProxiesRaw;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        Set<String> trustedProxies = parseTrustedProxies();

        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        String candidate = forwardedFor.split(",")[0].trim();
        return candidate.isEmpty() ? remoteAddr : candidate;
    }

    private Set<String> parseTrustedProxies() {
        if (trustedProxiesRaw == null || trustedProxiesRaw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}

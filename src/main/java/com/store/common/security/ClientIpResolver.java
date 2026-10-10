package com.store.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the client IP used by rate limiting without blindly trusting
 * X-Forwarded-For.
 *
 * Forwarded data is considered only when the immediate TCP peer is explicitly
 * configured as a trusted proxy. Even then, the first forwarded value must be a
 * numeric IP literal; hostnames and malformed values fall back to the peer address.
 * This keeps client-controlled strings from creating arbitrary rate-limit keys.
 */
@Component
public class ClientIpResolver {

    private final String trustedProxiesRaw;

    public ClientIpResolver(@Value("${app.trusted-proxies:}") String trustedProxiesRaw) {
        this.trustedProxiesRaw = trustedProxiesRaw;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        Set<String> trustedProxies = parseTrustedProxies();

        if (remoteAddr == null || trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        String candidate = forwardedFor.split(",", -1)[0].trim();
        String normalizedAddress = normalizeIpLiteral(candidate);
        return normalizedAddress == null ? remoteAddr : normalizedAddress;
    }

    /**
     * Parses only numeric IPv4/IPv6 literals. In particular, never resolve a
     * client-supplied hostname through DNS while deciding a security rate-limit key.
     */
    private String normalizeIpLiteral(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        if (candidate.indexOf(':') >= 0) {
            if (!candidate.matches("[0-9a-fA-F:.]+")) {
                return null;
            }
            try {
                InetAddress address = InetAddress.getByName(candidate);
                if (address instanceof Inet4Address || address instanceof Inet6Address) {
                    return address.getHostAddress();
                }
            } catch (UnknownHostException ignored) {
                // Invalid numeric IPv6 literal. Fall back to the trusted peer.
            }
            return null;
        }

        String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }

        int[] normalized = new int[4];
        for (int i = 0; i < octets.length; i++) {
            if (octets[i].isEmpty() || octets[i].length() > 3
                    || !octets[i].chars().allMatch(Character::isDigit)) {
                return null;
            }
            try {
                normalized[i] = Integer.parseInt(octets[i]);
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (normalized[i] > 255) {
                return null;
            }
        }

        return normalized[0] + "." + normalized[1] + "."
                + normalized[2] + "." + normalized[3];
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

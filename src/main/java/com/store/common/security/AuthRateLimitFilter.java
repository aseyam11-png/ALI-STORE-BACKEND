package com.store.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request-abuse defense on a small, fixed set of sensitive endpoints. It covers authentication
 * and visual-search traffic without applying a global per-request limit. It is keyed on the raw
 * remote address (never a
 * client-supplied X-Forwarded-* header, which would let an attacker spoof past the limit
 * or frame another client - see SecurityConfig for the equivalent no-trust-forwarded-headers
 * stance). Behind a reverse proxy this keys on the proxy's address unless the deployment
 * configures a trusted forwarding setup; documented as a known limitation.
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Integer> LIMITED_PATHS = Map.of(
            "/api/v1/auth/login", 10,
            "/api/v1/auth/register", 10,
            "/api/v1/auth/email/verify", 5,
            "/api/v1/auth/email/resend", 3,
            "/api/v1/visual-search", 5,
            "/api/v1/search/suggestions", 60,
            "/api/v1/ai/ask", 8,
            "/api/v1/ai/discover", 8
    );
    private static final long WINDOW_MILLIS = 60_000L;

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Integer maxRequests = LIMITED_PATHS.get(path);
        if (maxRequests != null) {
            String key = clientIpResolver.resolve(request) + ":" + path;
            if (!rateLimiter.tryConsume(key, maxRequests, WINDOW_MILLIS)) {
                writeTooManyRequests(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        ErrorResponse body = ErrorResponse.builder()
                .status(429)
                .message("Too many requests. Please try again later.")
                .timestamp(LocalDateTime.now())
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

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
import java.util.Set;

/**
 * Brute-force defense on the unauthenticated auth endpoints. Bounded to a small, fixed
 * set of paths rather than every request, and keyed on the raw remote address (never a
 * client-supplied X-Forwarded-* header, which would let an attacker spoof past the limit
 * or frame another client - see SecurityConfig for the equivalent no-trust-forwarded-headers
 * stance). Behind a reverse proxy this keys on the proxy's address unless the deployment
 * configures a trusted forwarding setup; documented as a known limitation.
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (LIMITED_PATHS.contains(path)) {
            String key = clientIpResolver.resolve(request) + ":" + path;
            if (!rateLimiter.tryConsume(key, MAX_REQUESTS_PER_WINDOW, WINDOW_MILLIS)) {
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

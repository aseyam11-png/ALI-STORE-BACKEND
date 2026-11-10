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
 * IP-scoped request-abuse defense for public and sensitive endpoints. Login also has
 * a database-backed per-account failed-attempt lockout in LoginAttemptService; these
 * controls are deliberately complementary.
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Integer> LIMITED_PATHS = Map.ofEntries(
            Map.entry("/api/v1/auth/login", 10),
            Map.entry("/api/v1/auth/admin/login", 10),
            Map.entry("/api/v1/auth/register", 10),
            Map.entry("/api/v1/auth/email/verify", 5),
            Map.entry("/api/v1/auth/email/resend", 3),
            Map.entry("/api/v1/auth/admin/email/verify", 5),
            Map.entry("/api/v1/auth/admin/email/resend", 3),
            Map.entry("/api/v1/auth/password/forgot", 3),
            Map.entry("/api/v1/auth/password/reset", 5),
            Map.entry("/api/v1/auth/password/change", 5),
            Map.entry("/api/v1/visual-search", 5),
            Map.entry("/api/v1/search/suggestions", 60),
            Map.entry("/api/v1/ai/ask", 8),
            Map.entry("/api/v1/ai/discover", 8)
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

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
 * Per-IP abuse limits for all API routes plus stricter ceilings for authentication and
 * expensive endpoints. Client identity comes from ClientIpResolver, which ignores forwarded
 * headers unless the immediate peer is an explicitly trusted proxy and validates the chain.
 *
 * This limiter is process-local; multi-instance production needs a shared atomic store and
 * complementary edge/WAF limits. Configure trusted proxy hops only for the real deployment.
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
        String clientIp = clientIpResolver.resolve(request);

        // A broad baseline limit applies to all API routes. Admin mutations get a
        // tighter per-IP ceiling, while specific expensive/authentication routes
        // below retain their stricter endpoint-specific limits.
        if (path.startsWith("/api/")) {
            boolean adminRoute = path.startsWith("/api/v1/admin");
            int maxApiRequests = adminRoute ? 120 : 300;
            String apiKey = "api:" + clientIp + (adminRoute ? ":admin" : ":general");
            if (!rateLimiter.tryConsume(apiKey, maxApiRequests, WINDOW_MILLIS)) {
                writeTooManyRequests(response);
                return;
            }
        }

        Integer maxRequests = LIMITED_PATHS.get(path);
        if (maxRequests != null) {
            String key = clientIp + ":" + path;
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

package com.vaultpay.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This is Redis-backed API rate limiter using a fixed-window counter per user per minute.
 *
 * DESIGN:
 *   Key format: rate_limit:{userEmail}:{YYYY-MM-DDTHH:mm}
 *   Each key tracks the request count within one minute.
 *   The key expires automatically after 2 minutes (TTL), so Redis self-cleans.
 *
 * LIMIT: 60 requests per user per minute for the /api/ endpoints.
 *
 * WHY THIS MATTERS:
 *   Without rate limiting, a single compromised account could spam thousands of
 *   transfer requests per second. This provides a lightweight defense layer.
 *
 * OncePerRequestFilter → Spring guarantees this filter runs exactly once per request,
 * even with request forwarding/dispatching.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only rate-limit authenticated requests to our API
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI();

        if (auth == null || !auth.isAuthenticated() || !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userEmail = auth.getName();
        String windowKey  = LocalDateTime.now().format(MINUTE_FORMATTER);
        String redisKey   = "rate_limit:" + userEmail + ":" + windowKey;

        try {
            // INCR is atomic in Redis — safe for concurrent requests from the same user
            Long requestCount = redisTemplate.opsForValue().increment(redisKey);

            // Set TTL on the first request of the window (subsequent INCRs won't reset the TTL)
            if (requestCount != null && requestCount == 1) {
                redisTemplate.expire(redisKey, Duration.ofMinutes(2));
            }

            if (requestCount != null && requestCount > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for user: {}", userEmail);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("""
                        {"status":429,"detail":"Too many requests. Please slow down."}
                        """);
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limiting unavailable for user {} - allowing request: {}", userEmail, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

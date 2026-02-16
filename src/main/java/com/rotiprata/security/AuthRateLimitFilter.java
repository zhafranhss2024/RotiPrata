package com.rotiprata.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.api.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";
    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";

    private static final RateLimitDefinition SIGNIN_LIMIT = new RateLimitDefinition(30, Duration.ofMinutes(5));
    private static final RateLimitDefinition FORGOT_PASSWORD_LIMIT = new RateLimitDefinition(2, Duration.ofHours(1));

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        RateLimitDefinition limit = resolveLimit(path);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(limit.keyPrefix() + ":" + ip, key -> newBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
            "rate_limited",
            "Too many requests. Try again later.",
            null,
            retryAfterSeconds
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private RateLimitDefinition resolveLimit(String path) {
        return switch (path) {
            case LOGIN_PATH -> SIGNIN_LIMIT.withKeyPrefix("login");
            case REGISTER_PATH -> SIGNIN_LIMIT.withKeyPrefix("register");
            case FORGOT_PASSWORD_PATH -> FORGOT_PASSWORD_LIMIT.withKeyPrefix("forgot-password");
            case RESET_PASSWORD_PATH -> SIGNIN_LIMIT.withKeyPrefix("reset-password");
            default -> null;
        };
    }

    private Bucket newBucket(RateLimitDefinition definition) {
        Bandwidth limit = Bandwidth.classic(
            definition.capacity(),
            Refill.intervally(definition.capacity(), definition.duration())
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0) {
                String candidate = parts[0].trim();
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitDefinition(int capacity, Duration duration, String keyPrefix) {
        RateLimitDefinition(int capacity, Duration duration) {
            this(capacity, duration, "auth");
        }

        RateLimitDefinition withKeyPrefix(String prefix) {
            return new RateLimitDefinition(capacity, duration, prefix);
        }
    }
}

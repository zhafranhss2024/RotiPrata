package com.rotiprata.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.api.common.response.ApiErrorResponse;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGIN_SESSIONS_PATH = "/api/auth/sessions";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String REGISTRATIONS_PATH = "/api/auth/registrations";
    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";
    private static final String PASSWORD_RESET_REQUESTS_PATH = "/api/auth/password-reset-requests";
    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";
    private static final String PASSWORD_PATH = "/api/auth/password";
    private static final String QUIZ_ANSWER_SUFFIX = "/quiz/answer";
    private static final String QUIZ_ANSWERS_SUFFIX = "/quiz/answers";

    private static final RateLimitDefinition SIGNIN_LIMIT = new RateLimitDefinition(30, Duration.ofMinutes(5));
    private static final RateLimitDefinition FORGOT_PASSWORD_LIMIT = new RateLimitDefinition(2, Duration.ofHours(1));
    private static final RateLimitDefinition QUIZ_ANSWER_LIMIT = new RateLimitDefinition(120, Duration.ofMinutes(1));

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        RateLimitDefinition limit = resolveLimit(method, path);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveRateLimitKey(path, request, limit);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limit));

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

    private RateLimitDefinition resolveLimit(String method, String path) {
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)) {
            return null;
        }
        if ("POST".equalsIgnoreCase(method)
            && path != null
            && path.startsWith("/api/lessons/")
            && (path.endsWith(QUIZ_ANSWER_SUFFIX) || path.endsWith(QUIZ_ANSWERS_SUFFIX))) {
            return QUIZ_ANSWER_LIMIT.withKeyPrefix("quiz-answer");
        }
        if ("POST".equalsIgnoreCase(method)) {
            return switch (path) {
                case LOGIN_PATH, LOGIN_SESSIONS_PATH -> SIGNIN_LIMIT.withKeyPrefix("login");
                case REGISTER_PATH, REGISTRATIONS_PATH -> SIGNIN_LIMIT.withKeyPrefix("register");
                case FORGOT_PASSWORD_PATH, PASSWORD_RESET_REQUESTS_PATH ->
                    FORGOT_PASSWORD_LIMIT.withKeyPrefix("forgot-password");
                case RESET_PASSWORD_PATH -> SIGNIN_LIMIT.withKeyPrefix("reset-password");
                default -> null;
            };
        }
        return switch (path) {
            case PASSWORD_PATH -> SIGNIN_LIMIT.withKeyPrefix("reset-password");
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

    private String resolveRateLimitKey(String path, HttpServletRequest request, RateLimitDefinition definition) {
        if (path != null
            && path.startsWith("/api/lessons/")
            && (path.endsWith(QUIZ_ANSWER_SUFFIX) || path.endsWith(QUIZ_ANSWERS_SUFFIX))) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
                String principal = authentication.getName().trim();
                if (!principal.isBlank() && !"anonymousUser".equalsIgnoreCase(principal)) {
                    return definition.keyPrefix() + ":user:" + principal;
                }
            }
        }
        return definition.keyPrefix() + ":ip:" + resolveClientIp(request);
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

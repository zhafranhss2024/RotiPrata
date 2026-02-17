package com.rotiprata.application;

import com.rotiprata.infrastructure.supabase.SupabaseAuthClient;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseSessionResponse;
import com.rotiprata.infrastructure.supabase.SupabaseSignupResponse;
import com.rotiprata.infrastructure.supabase.SupabaseUser;
import com.rotiprata.api.dto.AuthSessionResponse;
import com.rotiprata.api.dto.ForgotPasswordRequest;
import com.rotiprata.api.dto.LoginRequest;
import com.rotiprata.api.dto.RegisterRequest;
import com.rotiprata.api.dto.ResetPasswordRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final SupabaseAuthClient supabaseAuthClient;
    private final SupabaseAdminClient supabaseAdminClient;
    private final UserService userService;
    private final String frontendUrl;

    public AuthService(
        SupabaseAuthClient supabaseAuthClient,
        SupabaseAdminClient supabaseAdminClient,
        UserService userService,
        @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.supabaseAuthClient = supabaseAuthClient;
        this.supabaseAdminClient = supabaseAdminClient;
        this.userService = userService;
        this.frontendUrl = frontendUrl;
    }

    public AuthSessionResponse login(LoginRequest request) {
        try {
            SupabaseSessionResponse session = supabaseAuthClient.login(request.email(), request.password());
            return toAuthResponse(session, false, null);
        } catch (RestClientResponseException ex) {
            log.warn("Login failed for email {} status {}", request.email(), ex.getRawStatusCode());
            String body = ex.getResponseBodyAsString();
            if (body != null) {
                String normalized = body.toLowerCase();
                if (normalized.contains("email not confirmed") || normalized.contains("confirm your email")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not confirmed. Check your inbox.", ex);
                }
            }
            int status = ex.getRawStatusCode();
            if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNAUTHORIZED.value()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Authentication service unavailable", ex);
        }
    }

    public AuthSessionResponse register(RegisterRequest request) {
        try {
            if (!userService.isDisplayNameFormatValid(request.displayName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name must be 3-30 characters and use letters, numbers, dot, underscore, or hyphen.");
            }
            String normalizedDisplayName = userService.normalizeDisplayName(request.displayName());
            if (supabaseAdminClient.emailExists(request.email())) {
                log.warn("Registration failed: email already registered {}", request.email());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }
            if (userService.isDisplayNameTaken(normalizedDisplayName)) {
                log.warn("Registration failed: display name already taken {}", normalizedDisplayName);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Display name already taken");
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("display_name", request.displayName());
            if (request.isGenAlpha() != null) {
                metadata.put("is_gen_alpha", request.isGenAlpha());
            }

            String redirectTo = resolveRedirectTo(request.redirectTo(), "/auth/callback");
            SupabaseSignupResponse response = supabaseAuthClient.signup(
                request.email(),
                request.password(),
                metadata,
                redirectTo
            );

            if (response.getSession() == null) {
                SupabaseUser user = response.getUser();
                return new AuthSessionResponse(
                    null,
                    null,
                    null,
                    null,
                    user != null && user.getId() != null ? UUID.fromString(user.getId()) : null,
                    user != null ? user.getEmail() : request.email(),
                    true,
                    "Check your email to confirm your account."
                );
            }

            SupabaseUser user = response.getUser();
            if (user != null && user.getId() != null) {
                UUID userId = UUID.fromString(user.getId());
                userService.ensureProfile(
                    userId,
                    request.displayName(),
                    request.isGenAlpha(),
                    response.getSession().getAccessToken(),
                    false
                );
            }

            return toAuthResponse(response.getSession(), false, null);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String supabaseErrorCode = null;
            if (ex.getResponseHeaders() != null) {
                supabaseErrorCode = ex.getResponseHeaders().getFirst("x_sb_error_code");
            }
            log.warn(
                "Registration failed for email {} status {} supabase_error_code {} body {}",
                request.email(),
                ex.getRawStatusCode(),
                supabaseErrorCode,
                responseBody
            );
            String normalizedBody = responseBody == null ? "" : responseBody.toLowerCase();
            if (ex.getRawStatusCode() == HttpStatus.TOO_MANY_REQUESTS.value()
                || "over_email_send_rate_limit".equalsIgnoreCase(supabaseErrorCode)
                || normalizedBody.contains("rate limit")) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Email rate limit exceeded", ex);
            }
            if (isEmailAlreadyRegistered(ex)) {
                log.warn("Registration failed: email already registered {} status {}", request.email(), ex.getRawStatusCode());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to register user", ex);
        }
    }

    public void requestPasswordReset(ForgotPasswordRequest request) {
        try {
            String redirectTo = resolveRedirectTo(request.redirectTo(), "/reset-password");
            supabaseAuthClient.recoverPassword(request.email(), redirectTo);
        } catch (RestClientResponseException ex) {
            log.warn("Password reset email request failed for email {} status {}", request.email(), ex.getRawStatusCode());
            if (ex.getRawStatusCode() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Email rate limit exceeded", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to send reset email", ex);
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        try {
            supabaseAuthClient.updatePassword(request.accessToken(), request.password());
        } catch (RestClientResponseException ex) {
            log.warn("Password reset failed status {}", ex.getRawStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to reset password", ex);
        }
    }

    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            supabaseAuthClient.logout(accessToken);
        } catch (RestClientResponseException ex) {
            log.warn("Logout failed status {}", ex.getRawStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to logout", ex);
        }
    }

    public String buildOAuthUrl(String provider, String redirectTo) {
        return supabaseAuthClient.buildOAuthUrl(provider, redirectTo).toString();
    }

    private AuthSessionResponse toAuthResponse(SupabaseSessionResponse session, boolean requiresEmailConfirmation, String message) {
        SupabaseUser user = session.getUser();
        UUID userId = user != null && user.getId() != null ? UUID.fromString(user.getId()) : null;
        return new AuthSessionResponse(
            session.getAccessToken(),
            session.getRefreshToken(),
            session.getTokenType(),
            session.getExpiresIn(),
            userId,
            user != null ? user.getEmail() : null,
            requiresEmailConfirmation,
            message
        );
    }

    private String resolveRedirectTo(String redirectTo, String path) {
        if (redirectTo != null && !redirectTo.isBlank()) {
            return redirectTo;
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return null;
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private boolean isEmailAlreadyRegistered(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase();
        return normalized.contains("already registered")
            || normalized.contains("already exists")
            || normalized.contains("user already")
            || normalized.contains("email address already");
    }
}

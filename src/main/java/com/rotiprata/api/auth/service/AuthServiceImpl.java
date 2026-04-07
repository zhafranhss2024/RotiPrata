package com.rotiprata.api.auth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.rotiprata.api.auth.dto.AuthSessionResponse;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.api.zdto.ForgotPasswordRequest;
import com.rotiprata.api.zdto.LoginRequest;
import com.rotiprata.api.zdto.RegisterRequest;
import com.rotiprata.api.zdto.ResetPasswordRequest;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseAuthClient;
import com.rotiprata.infrastructure.supabase.SupabaseSessionResponse;
import com.rotiprata.infrastructure.supabase.SupabaseSignupResponse;
import com.rotiprata.infrastructure.supabase.SupabaseUser;

/**
 * Handles all authentication logic using Supabase.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final SupabaseAuthClient supabaseAuthClient;
    private final SupabaseAdminClient supabaseAdminClient;
    private final UserService userService;
    private final String frontendUrl;

    // Inject dependencies and frontend URL
    public AuthServiceImpl(SupabaseAuthClient supabaseAuthClient,
                           SupabaseAdminClient supabaseAdminClient,
                           UserService userService,
                           @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.supabaseAuthClient = supabaseAuthClient;
        this.supabaseAdminClient = supabaseAdminClient;
        this.userService = userService;
        this.frontendUrl = frontendUrl;
    }

    // Authenticate user and return session
    @Override
    public AuthSessionResponse login(LoginRequest request) {
        try {
            SupabaseSessionResponse session = supabaseAuthClient.login(request.email(), request.password());
            return toAuthResponse(session, false, null);
        } catch (RestClientResponseException ex) {
            log.warn("Login failed for email {} status {}", request.email(), getStatusCode(ex));
            String body = ex.getResponseBodyAsString();
            // Reject unconfirmed emails
            if (body != null && (body.toLowerCase().contains("email not confirmed") || body.toLowerCase().contains("confirm your email"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not confirmed. Check your inbox.", ex);
            }
            // Reject invalid credentials
            int status = getStatusCode(ex);
            if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNAUTHORIZED.value()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", ex);
            }
            // Handle upstream failures
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Authentication service unavailable", ex);
        }
    }

    // Register user with Supabase and create internal profile
    @Override
    public AuthSessionResponse register(RegisterRequest request) {
        try {
            // Validate display name
            if (!userService.isDisplayNameFormatValid(request.displayName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name must be 3-30 chars and use letters, numbers, dot, underscore, or hyphen.");
            }
            String normalizedDisplayName = userService.normalizeDisplayName(request.displayName());
            // Prevent duplicate email
            if (supabaseAdminClient.emailExists(request.email())) {
                log.warn("Registration failed: email already registered {}", request.email());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }
            // Prevent duplicate display name
            if (userService.isDisplayNameTaken(normalizedDisplayName)) {
                log.warn("Registration failed: display name already taken {}", normalizedDisplayName);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Display name already taken");
            }
            // Prepare metadata for Supabase
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("display_name", normalizedDisplayName);
            metadata.put("username", normalizedDisplayName);
            metadata.put("preferred_username", normalizedDisplayName);
            if (request.isGenAlpha() != null) metadata.put("is_gen_alpha", request.isGenAlpha());
            // Build redirect URL for email confirmation
            String redirectTo = resolveRedirectTo(request.redirectTo(), "/auth/finish");
            SupabaseSignupResponse response = supabaseAuthClient.signup(request.email(), request.password(), metadata, redirectTo);
            // Handle email confirmation required
            if (response.getSession() == null) {
                SupabaseUser user = response.getUser();
                if (user != null && user.getId() != null) {
                    userService.ensureProfileWithServiceRole(UUID.fromString(user.getId()), normalizedDisplayName, request.isGenAlpha(), false);
                }
                return new AuthSessionResponse(null, null, null, null, user != null && user.getId() != null ? UUID.fromString(user.getId()) : null, user != null ? user.getEmail() : request.email(), true, "Check your email to confirm your account.");
            }
            // Create internal profile for active session
            SupabaseUser user = response.getUser();
            if (user != null && user.getId() != null) {
                userService.ensureProfile(UUID.fromString(user.getId()), normalizedDisplayName, request.isGenAlpha(), response.getSession().getAccessToken(), false);
            }
            return toAuthResponse(response.getSession(), false, null);
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String supabaseErrorCode = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getFirst("x_sb_error_code") : null;
            log.warn("Registration failed for email {} status {} supabase_error_code {} body {}", request.email(), getStatusCode(ex), supabaseErrorCode, responseBody);
            // Handle rate limits
            if (getStatusCode(ex) == HttpStatus.TOO_MANY_REQUESTS.value() || "over_email_send_rate_limit".equalsIgnoreCase(supabaseErrorCode) || (responseBody != null && responseBody.toLowerCase().contains("rate limit"))) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Email rate limit exceeded", ex);
            }
            // Detect duplicate email from response
            if (isEmailAlreadyRegistered(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to register user", ex);
        }
    }

    // Request password reset email
    @Override
    public void requestPasswordReset(ForgotPasswordRequest request) {
        try {
            String redirectTo = resolveRedirectTo(request.redirectTo(), "/auth/finish");
            supabaseAuthClient.recoverPassword(request.email(), redirectTo);
        } catch (RestClientResponseException ex) {
            log.warn("Password reset email failed {}", getStatusCode(ex));
            if (getStatusCode(ex) == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Email rate limit exceeded", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to send reset email", ex);
        }
    }

    // Reset password using token
    @Override
    public void resetPassword(ResetPasswordRequest request) {
        try {
            supabaseAuthClient.updatePassword(request.accessToken(), request.password());
        } catch (RestClientResponseException ex) {
            log.warn("Password reset failed {}", getStatusCode(ex));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to reset password", ex);
        }
    }

    // Logout user and invalidate session
    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return; // Skip if token missing
        try {
            supabaseAuthClient.logout(accessToken);
        } catch (RestClientResponseException ex) {
            log.warn("Logout failed {}", getStatusCode(ex));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to logout", ex);
        }
    }

    // Convert Supabase session into internal response
    private AuthSessionResponse toAuthResponse(SupabaseSessionResponse session, boolean requiresEmailConfirmation, String message) {
        SupabaseUser user = session.getUser();
        UUID userId = user != null && user.getId() != null ? UUID.fromString(user.getId()) : null;
        return new AuthSessionResponse(session.getAccessToken(), session.getRefreshToken(), session.getTokenType(), session.getExpiresIn(), userId, user != null ? user.getEmail() : null, requiresEmailConfirmation, message);
    }

    // Build redirect URL for frontend
    private String resolveRedirectTo(String redirectTo, String path) {
        if (redirectTo != null && !redirectTo.isBlank()) return redirectTo;
        if (frontendUrl == null || frontendUrl.isBlank()) return null;
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    // Extract HTTP status code from exception
    private int getStatusCode(RestClientResponseException ex) {
        return ex.getStatusCode().value();
    }

    // Detect duplicate email from response
    private boolean isEmailAlreadyRegistered(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) return false;
        String normalized = body.toLowerCase();
        return normalized.contains("already registered") || normalized.contains("already exists") || normalized.contains("user already") || normalized.contains("email address already");
    }
}
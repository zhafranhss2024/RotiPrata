package com.rotiprata.application;

import com.rotiprata.infrastructure.supabase.SupabaseAuthClient;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final SupabaseAuthClient supabaseAuthClient;
    private final UserService userService;
    private final String frontendUrl;

    public AuthService(
        SupabaseAuthClient supabaseAuthClient,
        UserService userService,
        @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.supabaseAuthClient = supabaseAuthClient;
        this.userService = userService;
        this.frontendUrl = frontendUrl;
    }

    public AuthSessionResponse login(LoginRequest request) {
        try {
            SupabaseSessionResponse session = supabaseAuthClient.login(request.email(), request.password());
            return toAuthResponse(session, false, null);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password", ex);
        }
    }

    public AuthSessionResponse register(RegisterRequest request) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", request.username());
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
                userService.ensureProfile(userId, request.username(), request.isGenAlpha(), response.getSession().getAccessToken());
            }

            return toAuthResponse(response.getSession(), false, null);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to register user", ex);
        }
    }

    public void requestPasswordReset(ForgotPasswordRequest request) {
        try {
            String redirectTo = resolveRedirectTo(request.redirectTo(), "/reset-password");
            supabaseAuthClient.recoverPassword(request.email(), redirectTo);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to send reset email", ex);
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        try {
            supabaseAuthClient.updatePassword(request.accessToken(), request.password());
        } catch (RestClientResponseException ex) {
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
}

package com.rotiprata.api.auth.controller;

import com.rotiprata.api.auth.dto.AuthSessionResponse;
import com.rotiprata.api.auth.service.AuthService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.api.auth.response.DisplayNameAvailabilityResponse;
import com.rotiprata.api.auth.request.ForgotPasswordRequest;
import com.rotiprata.api.auth.request.LoginRequest;
import com.rotiprata.api.auth.request.LoginStreakTouchRequest;
import com.rotiprata.api.auth.response.LoginStreakTouchResponse;
import com.rotiprata.api.auth.request.RegisterRequest;
import com.rotiprata.api.auth.request.ResetPasswordRequest;
import com.rotiprata.api.auth.service.LoginStreakService;
import com.rotiprata.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes REST endpoints for the auth controller flows.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginStreakService loginStreakService;
    private final UserService userService;

    /**
     * Creates a auth controller instance with its collaborators.
     */
    public AuthController(
        AuthService authService,
        LoginStreakService loginStreakService,
        UserService userService
    ) {
        this.authService = authService;
        this.loginStreakService = loginStreakService;
        this.userService = userService;
    }

    /**
     * Creates the session.
     */
    @PostMapping("/sessions")
    public AuthSessionResponse createSession(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Handles login.
     */
    @Hidden
    @Deprecated
    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request) {
        return createSession(request);
    }

    /**
     * Creates the registration.
     */
    @PostMapping("/registrations")
    public AuthSessionResponse createRegistration(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Handles register.
     */
    @Hidden
    @Deprecated
    @PostMapping("/register")
    public AuthSessionResponse register(@Valid @RequestBody RegisterRequest request) {
        return createRegistration(request);
    }

    /**
     * Creates the password reset request.
     */
    @PostMapping("/password-reset-requests")
    public ResponseEntity<Void> createPasswordResetRequest(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles forgot password.
     */
    @Hidden
    @Deprecated
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return createPasswordResetRequest(request);
    }

    /**
     * Updates the password.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> updatePassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles reset password.
     */
    @Hidden
    @Deprecated
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return updatePassword(request);
    }

    /**
     * Deletes the session.
     */
    @DeleteMapping("/session")
    public ResponseEntity<Void> deleteSession(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles logout.
     */
    @Hidden
    @Deprecated
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        return deleteSession(authHeader);
    }

    /**
     * Updates the login streak.
     */
    @PutMapping("/login-streak")
    public LoginStreakTouchResponse updateLoginStreak(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) LoginStreakTouchRequest request
    ) {
        return loginStreakService.touchLoginStreak(
            SecurityUtils.getUserId(jwt),
            SecurityUtils.getAccessToken(),
            request == null ? null : request.timezone()
        );
    }

    /**
     * Converts the value into uch login streak.
     */
    @Hidden
    @Deprecated
    @PostMapping("/streak/touch")
    public LoginStreakTouchResponse touchLoginStreak(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) LoginStreakTouchRequest request
    ) {
        return updateLoginStreak(jwt, request);
    }

    /**
     * Handles display name availability.
     */
    @GetMapping("/display-name-availability")
    public DisplayNameAvailabilityResponse displayNameAvailability(
        @RequestParam(value = "displayName", required = false) String displayName,
        @RequestParam(value = "username", required = false) String username
    ) {
        String candidate = (displayName != null && !displayName.isBlank()) ? displayName : username;
        if (candidate == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Display name must be 3-30 characters and use letters, numbers, dot, underscore, or hyphen."
            );
        }
        if (!userService.isDisplayNameFormatValid(candidate)) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Display name must be 3-30 characters and use letters, numbers, dot, underscore, or hyphen."
            );
        }
        String normalized = userService.normalizeDisplayName(candidate);
        boolean available = !userService.isDisplayNameTaken(normalized);
        return new DisplayNameAvailabilityResponse(available, normalized);
    }

    /**
     * Handles username available.
     */
    @Hidden
    @Deprecated
    @GetMapping("/username-available")
    public DisplayNameAvailabilityResponse usernameAvailable(
        @RequestParam(value = "displayName", required = false) String displayName,
        @RequestParam(value = "username", required = false) String username
    ) {
        return displayNameAvailability(displayName, username);
    }

    /**
     * Extracts the bearer token.
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader;
    }
}

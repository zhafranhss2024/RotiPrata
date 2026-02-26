package com.rotiprata.api;

import com.rotiprata.application.AuthService;
import com.rotiprata.application.UserService;
import com.rotiprata.api.dto.AuthSessionResponse;
import com.rotiprata.api.dto.ForgotPasswordRequest;
import com.rotiprata.api.dto.LoginRequest;
import com.rotiprata.api.dto.RegisterRequest;
import com.rotiprata.api.dto.ResetPasswordRequest;
import com.rotiprata.api.dto.DisplayNameAvailabilityResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final String frontendUrl;

    public AuthController(
        AuthService authService,
        UserService userService,
        @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.authService = authService;
        this.userService = userService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/login")
    public AuthSessionResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthSessionResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login/google")
    public ResponseEntity<Void> loginGoogle(@RequestParam(value = "redirectTo", required = false) String redirectTo) {
        String callback = (redirectTo != null && !redirectTo.isBlank())
            ? redirectTo
            : UriComponentsBuilder.fromHttpUrl(frontendUrl)
                .path("/auth/callback")
                .build()
                .toUriString();
        String url = authService.buildOAuthUrl("google", callback);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, url)
            .build();
    }

    @GetMapping("/username-available")
    public DisplayNameAvailabilityResponse usernameAvailable(
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

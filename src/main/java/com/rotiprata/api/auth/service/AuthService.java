package com.rotiprata.api.auth.service;

import com.rotiprata.api.auth.dto.AuthSessionResponse;
import com.rotiprata.api.auth.request.ForgotPasswordRequest;
import com.rotiprata.api.auth.request.LoginRequest;
import com.rotiprata.api.auth.request.RegisterRequest;
import com.rotiprata.api.auth.request.ResetPasswordRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service interface for authentication-related operations.
 * Handles login, registration, password reset, and logout.
 */
public interface AuthService {

    /**
     * Login user using email and password.
     *
     * @param request login request containing email and password
     * @return AuthSessionResponse containing tokens and user info
     * @throws ResponseStatusException if login fails (invalid credentials, email not confirmed, or service error)
     */
    AuthSessionResponse login(LoginRequest request);

    /**
     * Register a new user.
     *
     * @param request register request containing email, password, display name, etc.
     * @return AuthSessionResponse (may require email confirmation)
     * @throws ResponseStatusException if registration fails (invalid input, email/display name already exists, rate limit, or service error)
     */
    AuthSessionResponse register(RegisterRequest request);

    /**
     * Send password reset email.
     *
     * @param request contains user email
     * @throws ResponseStatusException if sending reset email fails (rate limit exceeded or service error)
     */
    void requestPasswordReset(ForgotPasswordRequest request);

    /**
     * Reset password using access token.
     *
     * @param request contains new password and access token
     * @throws ResponseStatusException if resetting password fails
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Logout user and invalidate session.
     *
     * @param accessToken user's access token
     * @throws ResponseStatusException if logout fails
     */
    void logout(String accessToken);
}

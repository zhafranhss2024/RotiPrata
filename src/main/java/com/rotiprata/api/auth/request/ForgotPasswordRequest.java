package com.rotiprata.api.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the forgot password request payload accepted by the API layer.
 */
public record ForgotPasswordRequest(
    @Email @NotBlank String email,
    String redirectTo
) {}

package com.rotiprata.api.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the login request payload accepted by the API layer.
 */
public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}

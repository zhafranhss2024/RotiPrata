package com.rotiprata.api.auth.request;

import jakarta.validation.constraints.NotBlank;
import com.rotiprata.validation.PasswordPolicy;

/**
 * Represents the reset password request payload accepted by the API layer.
 */
public record ResetPasswordRequest(
    @NotBlank String accessToken,
    @NotBlank @PasswordPolicy String password
) {}

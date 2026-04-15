package com.rotiprata.api.auth.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.rotiprata.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the register request payload accepted by the API layer.
 */
public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank @PasswordPolicy String password,
    @NotBlank @JsonAlias("username") String displayName,
    Boolean isGenAlpha,
    String redirectTo
) {}

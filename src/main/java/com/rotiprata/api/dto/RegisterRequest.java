package com.rotiprata.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotBlank String username,
    Boolean isGenAlpha,
    String redirectTo
) {}

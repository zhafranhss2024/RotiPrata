package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
    @NotBlank String accessToken,
    @NotBlank String password
) {}

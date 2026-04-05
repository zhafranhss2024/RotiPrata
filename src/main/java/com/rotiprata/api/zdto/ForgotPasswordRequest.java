package com.rotiprata.api.zdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @Email @NotBlank String email,
    String redirectTo
) {}

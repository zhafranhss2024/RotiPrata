package com.rotiprata.api.zdto;

import jakarta.validation.constraints.NotBlank;
import com.rotiprata.validation.PasswordPolicy;

public record ResetPasswordRequest(
    @NotBlank String accessToken,
    @NotBlank @PasswordPolicy String password
) {}

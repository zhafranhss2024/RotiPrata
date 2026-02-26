package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupProfileRequest(
    @NotBlank(message = "Display name is required")
    @Size(min = 3, max = 30, message = "Display name must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Display name may only contain letters, numbers, dot, underscore, or hyphen")
    String displayName
) {}

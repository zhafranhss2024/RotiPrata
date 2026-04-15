package com.rotiprata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminFlagResolutionRequest(
    @NotBlank String status,
    @Size(max = 500) String feedback
) {}

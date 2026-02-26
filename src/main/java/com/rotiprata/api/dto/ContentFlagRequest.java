package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContentFlagRequest(
    @NotBlank @Size(max = 120) String reason,
    @Size(max = 1000) String description
) {}

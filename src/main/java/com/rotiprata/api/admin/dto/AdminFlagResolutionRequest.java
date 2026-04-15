package com.rotiprata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents the admin flag resolution request payload exchanged by the feature layer.
 */
public record AdminFlagResolutionRequest(
    @NotBlank String status,
    @Size(max = 500) String feedback
) {}

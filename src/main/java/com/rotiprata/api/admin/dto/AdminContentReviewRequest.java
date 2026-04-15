package com.rotiprata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents the admin content review request payload exchanged by the feature layer.
 */
public record AdminContentReviewRequest(
    @NotBlank String status,
    @Size(max = 500) String feedback
) {}

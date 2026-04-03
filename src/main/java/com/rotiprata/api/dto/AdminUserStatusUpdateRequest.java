package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserStatusUpdateRequest(
    @NotBlank String status
) {}

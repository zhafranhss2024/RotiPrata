package com.rotiprata.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserStatusUpdateRequest(
    @NotBlank String status
) {}

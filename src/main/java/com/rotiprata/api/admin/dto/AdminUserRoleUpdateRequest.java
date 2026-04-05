package com.rotiprata.api.admin.dto;

import com.rotiprata.domain.AppRole;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
    @NotNull AppRole role
) {}

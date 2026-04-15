package com.rotiprata.api.admin.dto;

import com.rotiprata.security.authorization.AppRole;
import jakarta.validation.constraints.NotNull;

/**
 * Represents the admin user role update request payload exchanged by the feature layer.
 */
public record AdminUserRoleUpdateRequest(
    @NotNull AppRole role
) {}

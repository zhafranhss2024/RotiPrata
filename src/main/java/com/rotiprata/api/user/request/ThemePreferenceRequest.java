package com.rotiprata.api.user.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents the theme preference request payload accepted by the API layer.
 */
public record ThemePreferenceRequest(@NotBlank String themePreference) {}

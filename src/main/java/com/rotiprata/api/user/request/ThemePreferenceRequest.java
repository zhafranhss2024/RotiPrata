package com.rotiprata.api.user.request;

import jakarta.validation.constraints.NotBlank;

public record ThemePreferenceRequest(@NotBlank String themePreference) {}

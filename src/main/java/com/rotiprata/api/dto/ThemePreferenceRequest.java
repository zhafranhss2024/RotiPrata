package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ThemePreferenceRequest(@NotBlank String themePreference) {}

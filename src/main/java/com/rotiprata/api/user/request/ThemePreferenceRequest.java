package com.rotiprata.api.zdto;

import jakarta.validation.constraints.NotBlank;

public record ThemePreferenceRequest(@NotBlank String themePreference) {}

package com.rotiprata.api.user.preference;

/**
 * Defines the theme preference values used across the feature.
 */
public enum ThemePreference {
    SYSTEM,
    LIGHT,
    DARK

    ;

    /**
     * Converts the value into json.
     */
    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    /**
     * Handles from json.
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static ThemePreference fromJson(String value) {
        return value == null ? null : ThemePreference.valueOf(value.toUpperCase());
    }
}

package com.rotiprata.domain;

public enum ThemePreference {
    SYSTEM,
    LIGHT,
    DARK

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ThemePreference fromJson(String value) {
        return value == null ? null : ThemePreference.valueOf(value.toUpperCase());
    }
}

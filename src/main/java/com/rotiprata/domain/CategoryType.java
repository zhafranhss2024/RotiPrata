package com.rotiprata.domain;

public enum CategoryType {
    SLANG,
    MEME,
    SOCIAL_PRACTICE,
    DANCE_TREND,
    OTHER

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static CategoryType fromJson(String value) {
        return value == null ? null : CategoryType.valueOf(value.toUpperCase());
    }
}

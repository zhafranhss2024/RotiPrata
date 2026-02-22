package com.rotiprata.domain;

public enum CategoryType {
    SLANG,
    MEME,
    SOCIAL_PRACTICE,
    DANCE_TREND,
    CULTURAL_REFERENCE,
    OTHER

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static CategoryType fromJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return CategoryType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}

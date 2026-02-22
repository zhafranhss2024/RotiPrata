package com.rotiprata.domain;

public enum AppRole {
    USER,
    ADMIN

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static AppRole fromJson(String value) {
        return value == null ? null : AppRole.valueOf(value.toUpperCase());
    }
}

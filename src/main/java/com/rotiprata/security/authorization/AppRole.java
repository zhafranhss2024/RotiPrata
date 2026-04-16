package com.rotiprata.security.authorization;

/**
 * Enforces the app role rules before requests reach the controller layer.
 */
public enum AppRole {
    USER,
    ADMIN

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
    public static AppRole fromJson(String value) {
        return value == null ? null : AppRole.valueOf(value.toUpperCase());
    }
}

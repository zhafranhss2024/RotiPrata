package com.rotiprata.domain;

public enum ContentStatus {
    PENDING,
    APPROVED,
    REJECTED

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ContentStatus fromJson(String value) {
        return value == null ? null : ContentStatus.valueOf(value.toUpperCase());
    }
}

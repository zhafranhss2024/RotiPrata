package com.rotiprata.domain;

public enum ContentType {
    VIDEO,
    IMAGE,
    TEXT

    ;

    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ContentType fromJson(String value) {
        return value == null ? null : ContentType.valueOf(value.toUpperCase());
    }
}

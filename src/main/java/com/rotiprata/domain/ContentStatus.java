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
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("ACCEPTED".equals(normalized)) {
            // Backward compatibility with older status value used in some rows.
            return APPROVED;
        }
        return ContentStatus.valueOf(normalized);
    }
}

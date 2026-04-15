package com.rotiprata.api.admin.dto;

public record AdminValidationError(
    String step,
    String fieldPath,
    String message,
    Integer questionIndex
) {}

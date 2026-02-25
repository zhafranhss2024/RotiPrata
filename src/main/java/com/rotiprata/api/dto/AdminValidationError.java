package com.rotiprata.api.dto;

public record AdminValidationError(
    String step,
    String fieldPath,
    String message,
    Integer questionIndex
) {}

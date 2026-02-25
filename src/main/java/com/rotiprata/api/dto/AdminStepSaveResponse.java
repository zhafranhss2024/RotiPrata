package com.rotiprata.api.dto;

import java.util.List;
import java.util.Map;

public record AdminStepSaveResponse(
    String step,
    boolean stepValid,
    List<AdminValidationError> errors,
    Map<String, Object> lessonSnapshot,
    Map<String, Boolean> completeness
) {}

package com.rotiprata.api.dto;

import java.util.List;
import java.util.Map;

public record AdminPublishLessonResponse(
    boolean success,
    String firstInvalidStep,
    List<AdminValidationError> errors,
    Map<String, Object> lessonSnapshot
) {}

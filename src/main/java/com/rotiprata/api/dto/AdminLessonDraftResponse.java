package com.rotiprata.api.dto;

import java.util.Map;
import java.util.UUID;

public record AdminLessonDraftResponse(
    UUID lessonId,
    Map<String, Boolean> completeness,
    Map<String, Object> lessonSnapshot
) {}

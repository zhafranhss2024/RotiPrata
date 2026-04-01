package com.rotiprata.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminLessonCategoryMoveResponse(
    UUID sourceCategoryId,
    UUID targetCategoryId,
    List<Map<String, Object>> sourceLessons,
    List<Map<String, Object>> targetLessons,
    Map<String, Object> movedLesson
) {}

package com.rotiprata.api.lesson.dto;

import java.util.List;
import java.util.UUID;

public record LessonHubCategoryResponse(
    UUID categoryId,
    String name,
    String type,
    String color,
    boolean isVirtual,
    List<LessonHubLessonResponse> lessons
) {}

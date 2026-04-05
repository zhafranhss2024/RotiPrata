package com.rotiprata.api.lesson.dto;

import java.util.List;

public record LessonHubResponse(
    List<LessonHubCategoryResponse> categories,
    LessonHubSummaryResponse summary
) {}

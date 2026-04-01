package com.rotiprata.api.dto;

import java.util.List;

public record LessonHubResponse(
    List<LessonHubCategoryResponse> categories,
    LessonHubSummaryResponse summary
) {}

package com.rotiprata.api.dto;

import java.util.List;

public record LessonHubResponse(
    List<LessonHubUnitResponse> units,
    LessonHubSummaryResponse summary
) {}

package com.rotiprata.api.dto;

import java.util.List;
import java.util.Map;

public record LessonFeedResponse(
    List<Map<String, Object>> items,
    boolean hasMore,
    int page,
    int pageSize
) {}

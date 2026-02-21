package com.rotiprata.api.dto;

import com.rotiprata.domain.Lesson;
import java.util.List;

public record LessonFeedResponse(List<Lesson> items, long total, int page, int pageSize) {}

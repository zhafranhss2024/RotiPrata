package com.rotiprata.api.lesson.dto;

public record LessonFeedRequest(
    String query,
    String difficulty,
    String duration,
    String sort,
    Integer page,
    Integer pageSize
) {}

package com.rotiprata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserLessonProgressResponse(
    String id,
    UUID lessonId,
    String lessonTitle,
    String status,
    int progressPercentage,
    String currentSection,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime lastAccessedAt
) {}

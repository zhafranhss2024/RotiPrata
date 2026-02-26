package com.rotiprata.api.dto;

import java.time.OffsetDateTime;

public record LessonProgressResponse(
    String status,
    int progressPercentage,
    String currentSection,
    int completedSections,
    int totalSections,
    String nextSectionId,
    boolean isEnrolled,
    int totalStops,
    int completedStops,
    String currentStopId,
    int remainingStops,
    String quizStatus,
    Integer heartsRemaining,
    OffsetDateTime heartsRefillAt,
    String nextStopType
) {}

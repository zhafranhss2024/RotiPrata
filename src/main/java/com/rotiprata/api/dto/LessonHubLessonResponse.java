package com.rotiprata.api.dto;

import java.util.UUID;

public record LessonHubLessonResponse(
    UUID lessonId,
    String title,
    Integer difficultyLevel,
    Integer estimatedMinutes,
    Integer xpReward,
    Integer completionCount,
    int progressPercentage,
    boolean completed,
    boolean current,
    boolean visuallyLocked
) {}

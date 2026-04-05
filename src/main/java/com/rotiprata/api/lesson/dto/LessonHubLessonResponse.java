package com.rotiprata.api.lesson.dto;

import java.util.UUID;

public record LessonHubLessonResponse(
    UUID lessonId,
    String title,
    String summary,
    Integer difficultyLevel,
    Integer estimatedMinutes,
    Integer xpReward,
    Integer completionCount,
    int progressPercentage,
    boolean completed,
    boolean current,
    boolean visuallyLocked
) {}

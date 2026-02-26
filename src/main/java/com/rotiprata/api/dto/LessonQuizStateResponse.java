package com.rotiprata.api.dto;

import java.util.List;

public record LessonQuizStateResponse(
    String attemptId,
    String status,
    Integer questionIndex,
    Integer totalQuestions,
    Integer correctCount,
    Integer earnedScore,
    Integer maxScore,
    LessonQuizQuestionResponse currentQuestion,
    LessonHeartsStatusResponse hearts,
    boolean canAnswer,
    boolean canRestart,
    List<String> wrongQuestionIds
) {}

package com.rotiprata.api.dto;

import java.util.List;

public record LessonQuizAnswerResponse(
    String attemptId,
    String status,
    boolean correct,
    String explanation,
    Integer questionIndex,
    Integer totalQuestions,
    Integer correctCount,
    Integer earnedScore,
    Integer maxScore,
    boolean passed,
    boolean quizCompleted,
    boolean blockedByHearts,
    LessonQuizQuestionResponse nextQuestion,
    LessonHeartsStatusResponse hearts,
    List<String> wrongQuestionIds
) {}

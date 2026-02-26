package com.rotiprata.application.quiz;

import java.util.Map;

public record LessonQuizGradeResult(
    boolean correct,
    Map<String, Object> normalizedResponse
) {}

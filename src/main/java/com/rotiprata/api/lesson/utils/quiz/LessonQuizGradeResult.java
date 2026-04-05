package com.rotiprata.api.lesson.utils.quiz;

import java.util.Map;

public record LessonQuizGradeResult(
    boolean correct,
    Map<String, Object> normalizedResponse
) {}

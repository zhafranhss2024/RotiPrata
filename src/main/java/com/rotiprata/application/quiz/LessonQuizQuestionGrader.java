package com.rotiprata.application.quiz;

import java.util.Map;

public interface LessonQuizQuestionGrader {
    String type();

    Map<String, Object> buildPayload(Map<String, Object> question);

    LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response);
}

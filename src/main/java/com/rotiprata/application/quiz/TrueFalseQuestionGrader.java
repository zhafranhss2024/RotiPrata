package com.rotiprata.application.quiz;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TrueFalseQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "true_false";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        return Map.of(
            "choices",
            List.of(
                Map.of("id", "true", "text", "True"),
                Map.of("id", "false", "text", "False")
            )
        );
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        Boolean correct = parseBooleanLike(question.get("correct_answer"));
        if (correct == null) {
            throw badRequest("true_false questions require correct_answer of true or false");
        }
        Object value = response == null ? null : response.get("value");
        if (value == null && response != null) {
            value = response.get("choiceId");
        }
        Boolean selected = parseBooleanLike(value);
        if (selected == null) {
            throw badRequest("Answer response is missing boolean value");
        }
        return new LessonQuizGradeResult(
            selected.equals(correct),
            Map.of("value", selected)
        );
    }

    private Boolean parseBooleanLike(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String str = asString(value);
        if (str == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(str)) {
            return true;
        }
        if ("false".equalsIgnoreCase(str)) {
            return false;
        }
        return null;
    }
}

package com.rotiprata.application.quiz;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MultipleChoiceQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "multiple_choice";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        Map<String, String> choices = resolveChoices(question);
        List<Map<String, Object>> payloadChoices = choices.entrySet().stream()
            .map(entry -> Map.<String, Object>of("id", entry.getKey(), "text", entry.getValue()))
            .toList();
        return Map.of("choices", payloadChoices);
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        Map<String, String> choices = resolveChoices(question);
        String selected = asString(response == null ? null : response.get("choiceId"));
        if (selected == null) {
            selected = asString(response == null ? null : response.get("selectedOption"));
        }
        if (selected == null) {
            throw badRequest("Answer response is missing choiceId");
        }
        String normalizedSelected = selected.toUpperCase();
        if (!choices.containsKey(normalizedSelected)) {
            throw badRequest("Invalid answer option");
        }
        String correct = asString(question.get("correct_answer"));
        if (correct == null) {
            throw badRequest("Question is missing correct_answer");
        }
        String normalizedCorrect = correct.toUpperCase();
        if (!choices.containsKey(normalizedCorrect)) {
            throw badRequest("Question has invalid correct_answer");
        }
        return new LessonQuizGradeResult(
            normalizedSelected.equals(normalizedCorrect),
            Map.of("choiceId", normalizedSelected)
        );
    }

    private Map<String, String> resolveChoices(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        Map<String, Object> rawChoices = asObject(options.get("choices"));
        if (rawChoices.isEmpty()) {
            rawChoices = options;
        }
        Map<String, String> choices = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawChoices.entrySet()) {
            String key = asString(entry.getKey());
            String value = asString(entry.getValue());
            if (key == null || value == null) {
                continue;
            }
            choices.put(key.toUpperCase(), value);
        }
        if (choices.size() < 2) {
            throw badRequest("multiple_choice questions require at least 2 choices");
        }
        return choices;
    }
}

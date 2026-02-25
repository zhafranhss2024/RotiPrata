package com.rotiprata.application.quiz;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ShortTextQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "short_text";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        Integer minLength = parseInteger(options.get("minLength"));
        Integer maxLength = parseInteger(options.get("maxLength"));
        return Map.of(
            "placeholder", asString(options.get("placeholder")) == null ? "Type your answer" : asString(options.get("placeholder")),
            "minLength", minLength == null ? 1 : minLength,
            "maxLength", maxLength == null ? 280 : maxLength
        );
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        String submitted = asString(response == null ? null : response.get("text"));
        if (submitted == null && response != null) {
            submitted = asString(response.get("value"));
        }
        if (submitted == null) {
            throw badRequest("Answer response is missing text");
        }
        String normalizedSubmitted = normalize(submitted);
        if (normalizedSubmitted == null) {
            throw badRequest("Answer response is empty");
        }

        Set<String> accepted = resolveAcceptedAnswers(question);
        boolean correct = accepted.contains(normalizedSubmitted);
        return new LessonQuizGradeResult(
            correct,
            Map.of("text", submitted)
        );
    }

    private Set<String> resolveAcceptedAnswers(Map<String, Object> question) {
        String raw = asString(question.get("correct_answer"));
        if (raw == null) {
            throw badRequest("short_text questions require correct_answer");
        }
        Set<String> accepted = new LinkedHashSet<>();
        if (raw.startsWith("{")) {
            Map<String, Object> parsed = parseJsonObject(raw, "correct_answer");
            List<?> list = asList(parsed.get("accepted"));
            for (Object item : list) {
                String normalized = normalize(item == null ? null : item.toString());
                if (normalized != null) {
                    accepted.add(normalized);
                }
            }
        } else if (raw.startsWith("[")) {
            Object parsed = readAnyJson(raw, "correct_answer");
            for (Object item : asList(parsed)) {
                String normalized = normalize(item == null ? null : item.toString());
                if (normalized != null) {
                    accepted.add(normalized);
                }
            }
        } else {
            String normalized = normalize(raw);
            if (normalized != null) {
                accepted.add(normalized);
            }
        }
        if (accepted.isEmpty()) {
            throw badRequest("short_text questions require at least one accepted answer");
        }
        return accepted;
    }

    private String normalize(String value) {
        String str = asString(value);
        if (str == null) {
            return null;
        }
        return str.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Object readAnyJson(String raw, String fieldName) {
        try {
            return OBJECT_MAPPER.readValue(raw, Object.class);
        } catch (Exception ex) {
            throw badRequest("Invalid JSON in " + fieldName);
        }
    }
}

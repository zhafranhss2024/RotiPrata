package com.rotiprata.application.quiz;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ClozeQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "cloze";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        Map<String, Map<String, String>> blankChoices = resolveBlankChoices(question);
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> blankOptions = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : blankChoices.entrySet()) {
            List<Map<String, Object>> choices = entry.getValue().entrySet().stream()
                .map(choice -> Map.<String, Object>of("id", choice.getKey(), "text", choice.getValue()))
                .toList();
            blankOptions.put(entry.getKey(), choices);
        }
        payload.put("blankOptions", blankOptions);
        return payload;
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        Map<String, Map<String, String>> blankChoices = resolveBlankChoices(question);
        Map<String, String> expectedAnswers = resolveExpectedAnswers(question, blankChoices);
        Map<String, Object> rawAnswers = resolveResponseMap(response, "answers");
        Map<String, String> answers = normalizeAnswers(rawAnswers, blankChoices);

        boolean correct = true;
        for (Map.Entry<String, String> expected : expectedAnswers.entrySet()) {
            if (!expected.getValue().equals(answers.get(expected.getKey()))) {
                correct = false;
                break;
            }
        }
        return new LessonQuizGradeResult(correct, Map.of("answers", answers));
    }

    private Map<String, Map<String, String>> resolveBlankChoices(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        Map<String, Object> rawBlankOptions = asObject(options.get("blankOptions"));
        if (rawBlankOptions.isEmpty()) {
            Map<String, Object> choices = asObject(options.get("choices"));
            if (!choices.isEmpty()) {
                rawBlankOptions = Map.of("blank_1", choices);
            }
        }
        Map<String, Map<String, String>> blankChoices = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawBlankOptions.entrySet()) {
            String blankId = asString(entry.getKey());
            if (blankId == null) {
                continue;
            }
            Map<String, String> choices = toChoices(entry.getValue());
            if (choices.size() < 2) {
                throw badRequest("cloze options for " + blankId + " must include at least 2 choices");
            }
            blankChoices.put(blankId, choices);
        }
        if (blankChoices.isEmpty()) {
            throw badRequest("cloze questions require options.blankOptions");
        }
        return blankChoices;
    }

    private Map<String, String> toChoices(Object value) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Object> asMap = asObject(value);
        if (!asMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : asMap.entrySet()) {
                String id = asString(entry.getKey());
                String text = asString(entry.getValue());
                if (id != null && text != null) {
                    out.put(id, text);
                }
            }
            return out;
        }
        for (Object item : asList(value)) {
            Map<String, Object> choice = asObject(item);
            String id = asString(choice.get("id"));
            String text = asString(choice.get("text"));
            if (id != null && text != null) {
                out.put(id, text);
            }
        }
        return out;
    }

    private Map<String, String> resolveExpectedAnswers(
        Map<String, Object> question,
        Map<String, Map<String, String>> blankChoices
    ) {
        String correctRaw = asString(question.get("correct_answer"));
        if (correctRaw == null) {
            throw badRequest("cloze questions require correct_answer");
        }
        Map<String, String> expected = new LinkedHashMap<>();
        Map<String, Object> parsedMap = correctRaw.startsWith("{")
            ? parseJsonObject(correctRaw, "correct_answer")
            : Map.of();
        if (!parsedMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : parsedMap.entrySet()) {
                String blankId = asString(entry.getKey());
                String choiceId = asString(entry.getValue());
                if (blankId == null || choiceId == null) {
                    continue;
                }
                expected.put(blankId, choiceId);
            }
        } else if (blankChoices.size() == 1) {
            String blankId = blankChoices.keySet().iterator().next();
            expected.put(blankId, correctRaw);
        } else {
            throw badRequest("cloze correct_answer must be a JSON object");
        }
        for (Map.Entry<String, Map<String, String>> entry : blankChoices.entrySet()) {
            String answer = expected.get(entry.getKey());
            if (answer == null || !entry.getValue().containsKey(answer)) {
                throw badRequest("cloze correct_answer must map each blank to a valid choice id");
            }
        }
        return expected;
    }

    private Map<String, String> normalizeAnswers(
        Map<String, Object> rawAnswers,
        Map<String, Map<String, String>> blankChoices
    ) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String blankId : blankChoices.keySet()) {
            String choiceId = asString(rawAnswers.get(blankId));
            if (choiceId == null) {
                throw badRequest("Missing answer for blank " + blankId);
            }
            if (!blankChoices.get(blankId).containsKey(choiceId)) {
                throw badRequest("Invalid choice for blank " + blankId);
            }
            normalized.put(blankId, choiceId);
        }
        return normalized;
    }
}

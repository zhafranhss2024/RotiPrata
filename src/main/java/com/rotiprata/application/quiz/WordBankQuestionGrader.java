package com.rotiprata.application.quiz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WordBankQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "word_bank";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        List<Map<String, String>> tokens = resolveTokens(question);
        return Map.of("tokens", tokens);
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        List<Map<String, String>> tokens = resolveTokens(question);
        Set<String> validTokenIds = new LinkedHashSet<>();
        for (Map<String, String> token : tokens) {
            validTokenIds.add(token.get("id"));
        }

        List<String> expectedOrder = resolveExpectedOrder(question, validTokenIds);
        List<String> selectedOrder = resolveSelectedOrder(response, validTokenIds);
        boolean correct = expectedOrder.equals(selectedOrder);

        return new LessonQuizGradeResult(
            correct,
            Map.of("tokenOrder", selectedOrder)
        );
    }

    private List<Map<String, String>> resolveTokens(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        List<?> rawTokens = asList(options.get("tokens"));
        List<Map<String, String>> tokens = new ArrayList<>();
        for (Object item : rawTokens) {
            Map<String, Object> token = asObject(item);
            String id = asString(token.get("id"));
            String text = asString(token.get("text"));
            if (id != null && text != null) {
                tokens.add(Map.of("id", id, "text", text));
            }
        }
        if (tokens.size() < 2) {
            throw badRequest("word_bank questions require options.tokens with at least 2 tokens");
        }
        return tokens;
    }

    private List<String> resolveExpectedOrder(Map<String, Object> question, Set<String> validTokenIds) {
        String raw = asString(question.get("correct_answer"));
        if (raw == null) {
            throw badRequest("word_bank questions require correct_answer");
        }
        List<String> expected = new ArrayList<>();
        if (raw.startsWith("[")) {
            for (Object item : asList(readAnyJson(raw, "correct_answer"))) {
                String tokenId = asString(item);
                if (tokenId != null) {
                    expected.add(tokenId);
                }
            }
        } else if (raw.startsWith("{")) {
            Map<String, Object> parsed = parseJsonObject(raw, "correct_answer");
            List<?> order = asList(parsed.get("order"));
            for (Object item : order) {
                String tokenId = asString(item);
                if (tokenId != null) {
                    expected.add(tokenId);
                }
            }
        } else {
            throw badRequest("word_bank correct_answer must be JSON array or object with order");
        }
        validateOrder(expected, validTokenIds, "correct_answer");
        return expected;
    }

    private List<String> resolveSelectedOrder(Map<String, Object> response, Set<String> validTokenIds) {
        Object rawOrder = response == null ? null : response.get("tokenOrder");
        if (rawOrder == null && response != null) {
            rawOrder = response.get("order");
        }
        List<String> selected = new ArrayList<>();
        for (Object item : asList(rawOrder)) {
            String tokenId = asString(item);
            if (tokenId != null) {
                selected.add(tokenId);
            }
        }
        validateOrder(selected, validTokenIds, "response.tokenOrder");
        return selected;
    }

    private void validateOrder(List<String> order, Set<String> validTokenIds, String fieldName) {
        if (order.isEmpty()) {
            throw badRequest("word_bank " + fieldName + " is required");
        }
        Set<String> unique = new LinkedHashSet<>(order);
        if (unique.size() != order.size()) {
            throw badRequest("word_bank " + fieldName + " cannot contain duplicate token ids");
        }
        if (!validTokenIds.containsAll(order)) {
            throw badRequest("word_bank " + fieldName + " contains unknown token ids");
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

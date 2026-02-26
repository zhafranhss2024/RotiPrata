package com.rotiprata.application.quiz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MatchPairsQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "match_pairs";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        Map<String, List<Map<String, String>>> pairs = resolvePairs(question);
        return Map.of("left", pairs.get("left"), "right", pairs.get("right"));
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        Map<String, List<Map<String, String>>> pairs = resolvePairs(question);
        Set<String> leftIds = pairs.get("left").stream().map(item -> item.get("id")).collect(Collectors.toSet());
        Set<String> rightIds = pairs.get("right").stream().map(item -> item.get("id")).collect(Collectors.toSet());
        Map<String, String> expected = resolveExpected(question, leftIds, rightIds);

        Map<String, Object> rawPairs = resolveResponseMap(response, "pairs");
        if (rawPairs == response) {
            rawPairs = resolveResponseMap(response, "answers");
        }
        Map<String, String> selectedPairs = new LinkedHashMap<>();
        for (String leftId : leftIds) {
            String rightId = asString(rawPairs.get(leftId));
            if (rightId == null) {
                throw badRequest("Missing pair for left item " + leftId);
            }
            if (!rightIds.contains(rightId)) {
                throw badRequest("Invalid right-side id for left item " + leftId);
            }
            selectedPairs.put(leftId, rightId);
        }

        boolean correct = true;
        for (Map.Entry<String, String> expectedEntry : expected.entrySet()) {
            if (!expectedEntry.getValue().equals(selectedPairs.get(expectedEntry.getKey()))) {
                correct = false;
                break;
            }
        }

        return new LessonQuizGradeResult(correct, Map.of("pairs", selectedPairs));
    }

    private Map<String, List<Map<String, String>>> resolvePairs(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        List<Map<String, String>> left = parseSide(options.get("left"), "left");
        List<Map<String, String>> right = parseSide(options.get("right"), "right");
        if (left.size() < 2 || right.size() < 2) {
            throw badRequest("match_pairs questions require at least 2 left and 2 right items");
        }
        return Map.of("left", left, "right", right);
    }

    private List<Map<String, String>> parseSide(Object raw, String label) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Object item : asList(raw)) {
            Map<String, Object> map = asObject(item);
            String id = asString(map.get("id"));
            String text = asString(map.get("text"));
            if (id != null && text != null) {
                out.add(Map.of("id", id, "text", text));
            }
        }
        if (out.isEmpty()) {
            throw badRequest("match_pairs questions require options." + label + " items");
        }
        return out;
    }

    private Map<String, String> resolveExpected(
        Map<String, Object> question,
        Set<String> leftIds,
        Set<String> rightIds
    ) {
        String correctRaw = asString(question.get("correct_answer"));
        if (correctRaw == null) {
            throw badRequest("match_pairs questions require correct_answer");
        }
        Map<String, Object> parsed = parseJsonObject(correctRaw, "correct_answer");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String leftId : leftIds) {
            String rightId = asString(parsed.get(leftId));
            if (rightId == null || !rightIds.contains(rightId)) {
                throw badRequest("match_pairs correct_answer must map each left id to a valid right id");
            }
            expected.put(leftId, rightId);
        }
        return expected;
    }
}

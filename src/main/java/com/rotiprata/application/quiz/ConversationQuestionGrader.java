package com.rotiprata.application.quiz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConversationQuestionGrader extends AbstractLessonQuizQuestionGrader {
    @Override
    public String type() {
        return "conversation";
    }

    @Override
    public Map<String, Object> buildPayload(Map<String, Object> question) {
        return Map.of("turns", resolveTurns(question));
    }

    @Override
    public LessonQuizGradeResult grade(Map<String, Object> question, Map<String, Object> response) {
        List<Map<String, Object>> turns = resolveTurns(question);
        Map<String, String> expected = resolveExpected(question, turns);
        Map<String, Object> rawAnswers = resolveResponseMap(response, "answers");
        Map<String, String> answers = new LinkedHashMap<>();

        for (Map<String, Object> turn : turns) {
            String turnId = asString(turn.get("id"));
            String selectedReply = asString(rawAnswers.get(turnId));
            if (selectedReply == null) {
                throw badRequest("Missing answer for conversation turn " + turnId);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> replies = (List<Map<String, Object>>) turn.get("replies");
            boolean valid = replies.stream()
                .map(reply -> asString(reply.get("id")))
                .anyMatch(selectedReply::equals);
            if (!valid) {
                throw badRequest("Invalid reply id for conversation turn " + turnId);
            }
            answers.put(turnId, selectedReply);
        }

        boolean correct = true;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(answers.get(entry.getKey()))) {
                correct = false;
                break;
            }
        }
        return new LessonQuizGradeResult(correct, Map.of("answers", answers));
    }

    private List<Map<String, Object>> resolveTurns(Map<String, Object> question) {
        Map<String, Object> options = asObject(question.get("options"));
        List<?> rawTurns = asList(options.get("turns"));
        List<Map<String, Object>> turns = new ArrayList<>();
        for (Object rawTurn : rawTurns) {
            Map<String, Object> turn = asObject(rawTurn);
            String turnId = asString(turn.get("id"));
            String prompt = asString(turn.get("prompt"));
            if (turnId == null || prompt == null) {
                continue;
            }
            List<Map<String, Object>> replies = new ArrayList<>();
            for (Object rawReply : asList(turn.get("replies"))) {
                Map<String, Object> reply = asObject(rawReply);
                String replyId = asString(reply.get("id"));
                String replyText = asString(reply.get("text"));
                if (replyId == null || replyText == null) {
                    continue;
                }
                replies.add(Map.of("id", replyId, "text", replyText));
            }
            if (replies.size() < 2) {
                throw badRequest("conversation turns require at least 2 replies");
            }
            turns.add(Map.of("id", turnId, "prompt", prompt, "replies", replies));
        }
        if (turns.isEmpty()) {
            throw badRequest("conversation questions require options.turns");
        }
        return turns;
    }

    private Map<String, String> resolveExpected(Map<String, Object> question, List<Map<String, Object>> turns) {
        Map<String, Object> parsed = parseJsonObject(question.get("correct_answer") == null
            ? null
            : question.get("correct_answer").toString(), "correct_answer");
        Map<String, String> expected = new LinkedHashMap<>();
        for (Map<String, Object> turn : turns) {
            String turnId = asString(turn.get("id"));
            String replyId = asString(parsed.get(turnId));
            if (replyId == null) {
                throw badRequest("conversation correct_answer missing turn " + turnId);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> replies = (List<Map<String, Object>>) turn.get("replies");
            boolean valid = replies.stream()
                .map(reply -> asString(reply.get("id")))
                .anyMatch(replyId::equals);
            if (!valid) {
                throw badRequest("conversation correct_answer contains invalid reply for turn " + turnId);
            }
            expected.put(turnId, replyId);
        }
        return expected;
    }
}

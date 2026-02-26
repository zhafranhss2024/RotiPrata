package com.rotiprata.application.quiz;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LessonQuizGraderRegistry {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "multiple_choice",
        "true_false",
        "cloze",
        "word_bank",
        "conversation",
        "match_pairs",
        "short_text"
    );

    private final Map<String, LessonQuizQuestionGrader> gradersByType;

    public LessonQuizGraderRegistry(List<LessonQuizQuestionGrader> graders) {
        this.gradersByType = graders.stream().collect(Collectors.toMap(LessonQuizQuestionGrader::type, Function.identity()));
    }

    public LessonQuizQuestionGrader require(String type) {
        String normalized = normalize(type);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "unsupported question type for this release"
            );
        }
        LessonQuizQuestionGrader grader = gradersByType.get(normalized);
        if (grader == null) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Question grader is not configured for type: " + normalized
            );
        }
        return grader;
    }

    public boolean isSupported(String type) {
        return SUPPORTED_TYPES.contains(normalize(type));
    }

    private String normalize(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toLowerCase();
    }
}

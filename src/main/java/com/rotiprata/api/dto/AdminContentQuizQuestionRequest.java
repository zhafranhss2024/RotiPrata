package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AdminContentQuizQuestionRequest(
    @JsonProperty("question_text") String questionText,
    @JsonProperty("options") Map<String, Object> options,
    @JsonProperty("correct_answer") String correctAnswer,
    @JsonProperty("explanation") String explanation,
    @JsonProperty("points") Integer points,
    @JsonProperty("order_index") Integer orderIndex
) {}

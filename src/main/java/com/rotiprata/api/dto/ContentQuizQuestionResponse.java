package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ContentQuizQuestionResponse(
    @JsonProperty("id") String id,
    @JsonProperty("quiz_id") String quizId,
    @JsonProperty("question_text") String questionText,
    @JsonProperty("question_type") String questionType,
    @JsonProperty("media_url") String mediaUrl,
    @JsonProperty("options") Map<String, Object> options,
    @JsonProperty("correct_answer") String correctAnswer,
    @JsonProperty("explanation") String explanation,
    @JsonProperty("points") Integer points,
    @JsonProperty("order_index") Integer orderIndex,
    @JsonProperty("created_at") String createdAt
) {}

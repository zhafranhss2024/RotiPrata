package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ContentQuizResponse(
    @JsonProperty("id") String id,
    @JsonProperty("lesson_id") String lessonId,
    @JsonProperty("content_id") String contentId,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("quiz_type") String quizType,
    @JsonProperty("time_limit_seconds") Integer timeLimitSeconds,
    @JsonProperty("passing_score") Integer passingScore,
    @JsonProperty("is_active") Boolean isActive,
    @JsonProperty("archived_at") String archivedAt,
    @JsonProperty("created_by") String createdBy,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("updated_at") String updatedAt,
    @JsonProperty("questions") List<ContentQuizQuestionResponse> questions
) {}

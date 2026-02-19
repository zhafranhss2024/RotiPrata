package com.rotiprata.api.dto;

import java.util.UUID;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BrowsingHistoryDTO {

    private UUID id;
    
    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("content_id")
    private UUID contentId;

    @JsonProperty("lesson_id")
    private UUID lessonId;

    @JsonProperty("viewed_at")
    private Instant viewedAt;

}

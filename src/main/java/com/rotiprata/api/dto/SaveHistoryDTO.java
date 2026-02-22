package com.rotiprata.api.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveHistoryDTO {


    @JsonProperty("item_id")
    private String itemId; 

    @JsonProperty("content_id")
    private String contentId;

    @JsonProperty("lesson_id")
    private String lessonId;

    @JsonProperty("viewed_at")  
    private Instant viewedAt;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }
}

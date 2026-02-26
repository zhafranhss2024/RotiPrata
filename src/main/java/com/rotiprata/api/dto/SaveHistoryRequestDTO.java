package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveHistoryRequestDTO {

    private String contentId;
    private String lessonId;
    private String title;

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

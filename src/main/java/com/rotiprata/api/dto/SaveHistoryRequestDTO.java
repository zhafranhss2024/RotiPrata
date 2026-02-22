package com.rotiprata.api.dto;

public class SaveHistoryRequestDTO {

    private String content_id; 
    private String lesson_id;  

    public String getContentId() { return content_id; }
    public void setContentId(String content_id) { this.content_id = content_id; }

    public String getLessonId() { return lesson_id; }
    public void setLessonId(String lesson_id) { this.lesson_id = lesson_id; }
}

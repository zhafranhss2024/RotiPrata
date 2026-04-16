package com.rotiprata.api.lesson.response;

import com.rotiprata.api.lesson.dto.LessonProgressResponse;

/**
 * Represents the section complete response payload returned by the API layer.
 */
public record SectionCompleteResponse(LessonProgressResponse progress) {}

package com.rotiprata.api.feed.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Defines the content-to-lesson link operations exposed to the API layer.
 */
public interface ContentLessonLinkService {

    /**
     * Resolves linked lessons for the given content ids.
     */
    Map<UUID, List<LinkedLesson>> resolveLinkedLessons(Set<UUID> contentIds);

    /**
     * Replaces the linked lessons for a content item.
     */
    void replaceContentLessonLinks(UUID contentId, List<UUID> lessonIds);

    /**
     * Represents a resolved lesson link for a content item.
     */
    record LinkedLesson(UUID lessonId, String lessonTitle, UUID categoryId, LinkSource source) {}

    /**
     * Identifies the source used to derive a lesson link.
     */
    enum LinkSource {
        LESSON_CONCEPT,
        QUIZ_FALLBACK
    }
}

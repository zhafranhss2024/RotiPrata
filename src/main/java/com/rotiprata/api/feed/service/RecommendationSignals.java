package com.rotiprata.api.feed.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record RecommendationSignals(
    Map<UUID, LessonProgressSignal> lessonProgressByLessonId,
    Set<UUID> likedContentIds,
    Set<UUID> savedContentIds,
    Set<UUID> sharedContentIds,
    Set<UUID> browsedContentIds,
    Set<UUID> masteredContentIds,
    Map<String, Integer> tagAffinity,
    Map<UUID, Integer> categoryAffinity,
    Map<UUID, Integer> creatorAffinity,
    List<String> recentSearchTerms,
    Map<UUID, Integer> recentImpressionCounts
) {
    record LessonProgressSignal(String status, int progressPercentage, OffsetDateTime lastAccessedAt) {}
}

package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationSignalServiceTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    private RecommendationSignalService recommendationSignalService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        recommendationSignalService = new RecommendationSignalService(supabaseAdminRestClient);
        userId = UUID.randomUUID();
    }

    @Test
    void loadSignals_shouldAggregateAffinitiesAndStableImpressions() {
        UUID likedContentId = UUID.randomUUID();
        UUID savedAndBrowsedContentId = UUID.randomUUID();
        UUID sharedContentId = UUID.randomUUID();
        UUID masteredContentId = UUID.randomUUID();
        UUID categoryA = UUID.randomUUID();
        UUID categoryB = UUID.randomUUID();
        UUID creatorA = UUID.randomUUID();
        UUID creatorB = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        stubTable("content_likes", List.of(Map.of("content_id", likedContentId.toString()), Map.of("content_id", "bad-uuid")));
        stubTable("content_saves", List.of(Map.of("content_id", savedAndBrowsedContentId.toString())));
        stubTable("content_shares", List.of(Map.of("content_id", sharedContentId.toString())));
        stubTable("browsing_history", List.of(Map.of("content_id", savedAndBrowsedContentId.toString())));
        stubTable("user_concepts_mastered", List.of(Map.of("content_id", masteredContentId.toString())));
        stubTable("user_lesson_progress", List.of(
            Map.of(
                "lesson_id", lessonId.toString(),
                "status", "in_progress",
                "progress_percentage", 45,
                "last_accessed_at", OffsetDateTime.now().minusHours(1).toString()
            ),
            Map.of("lesson_id", "bad-lesson", "status", "completed", "progress_percentage", 100)
        ));
        stubTable("search_history", List.of(
            Map.of("query", "Slang!!! clip"),
            Map.of("query", "meme lore")
        ));
        stubTable("recommendation_impressions", List.of(
            Map.of("content_id", likedContentId.toString(), "created_at", OffsetDateTime.now().minusMinutes(20).toString()),
            Map.of("content_id", likedContentId.toString(), "created_at", OffsetDateTime.now().minusMinutes(5).toString()),
            Map.of("content_id", "bad-uuid", "created_at", "bad-date")
        ));

        stubTable("content", List.of(
            Map.of("id", likedContentId.toString(), "category_id", categoryA.toString(), "creator_id", creatorA.toString()),
            Map.of("id", savedAndBrowsedContentId.toString(), "category_id", categoryA.toString(), "creator_id", creatorB.toString()),
            Map.of("id", sharedContentId.toString(), "category_id", categoryB.toString(), "creator_id", creatorA.toString())
        ));
        stubTable("content_tags", List.of(
            Map.of("content_id", likedContentId.toString(), "tag", "Slang"),
            Map.of("content_id", savedAndBrowsedContentId.toString(), "tag", "meme"),
            Map.of("content_id", sharedContentId.toString(), "tag", "slang"),
            Map.of("content_id", sharedContentId.toString(), "tag", " ")
        ));

        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        assertEquals(Set.of(likedContentId), signals.likedContentIds());
        assertEquals(Set.of(savedAndBrowsedContentId), signals.savedContentIds());
        assertEquals(Set.of(sharedContentId), signals.sharedContentIds());
        assertEquals(Set.of(savedAndBrowsedContentId), signals.browsedContentIds());
        assertEquals(Set.of(masteredContentId), signals.masteredContentIds());
        assertEquals(8, signals.tagAffinity().get("slang"));
        assertEquals(5, signals.tagAffinity().get("meme"));
        assertEquals(8, signals.categoryAffinity().get(categoryA));
        assertEquals(5, signals.categoryAffinity().get(categoryB));
        assertEquals(8, signals.creatorAffinity().get(creatorA));
        assertEquals(5, signals.creatorAffinity().get(creatorB));
        assertEquals(1, signals.recentImpressionCounts().get(likedContentId));
        assertEquals("in_progress", signals.lessonProgressByLessonId().get(lessonId).status());
        assertTrue(signals.recentSearchTerms().containsAll(List.of("slang", "clip", "meme", "lore")));
    }

    @Test
    void loadSignals_shouldSkipAffinityQueriesWhenThereAreNoInteractions() {
        stubTable("content_likes", List.of());
        stubTable("content_saves", List.of());
        stubTable("content_shares", List.of());
        stubTable("browsing_history", List.of());
        stubTable("user_concepts_mastered", List.of());
        stubTable("user_lesson_progress", List.of());
        stubTable("search_history", List.of());
        stubTable("recommendation_impressions", List.of());

        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        assertTrue(signals.tagAffinity().isEmpty());
        assertTrue(signals.categoryAffinity().isEmpty());
        assertTrue(signals.creatorAffinity().isEmpty());
        verify(supabaseAdminRestClient, never()).getList(eq("content"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    @Test
    void loadSignals_shouldIgnoreMissingImpressionTableWhileSignalsRollOut() {
        stubTable("content_likes", List.of());
        stubTable("content_saves", List.of());
        stubTable("content_shares", List.of());
        stubTable("browsing_history", List.of());
        stubTable("user_concepts_mastered", List.of());
        stubTable("user_lesson_progress", List.of());
        stubTable("search_history", List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation_impressions does not exist"));

        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        assertTrue(signals.recentImpressionCounts().isEmpty());
    }

    private void stubTable(String table, List<Map<String, Object>> rows) {
        when(supabaseAdminRestClient.getList(eq(table), anyString(), any(TypeReference.class)))
            .thenReturn(rows);
    }
}

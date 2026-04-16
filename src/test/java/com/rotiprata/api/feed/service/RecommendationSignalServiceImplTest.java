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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers recommendation signal service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationSignalServiceImplTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    private RecommendationSignalService recommendationSignalService;
    private UUID userId;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        recommendationSignalService = new RecommendationSignalServiceImpl(supabaseAdminRestClient);
        userId = UUID.randomUUID();
    }

    /** Verifies the signal service aggregates affinities, lesson progress, impressions, and tokenized searches. */
    @Test
    void loadSignals_ShouldAggregateAffinitiesAndStableImpressions_WhenRowsAreValid() {
        // arrange
        UUID likedContentId = UUID.randomUUID();
        UUID savedAndBrowsedContentId = UUID.randomUUID();
        UUID sharedContentId = UUID.randomUUID();
        UUID masteredContentId = UUID.randomUUID();
        UUID categoryA = UUID.randomUUID();
        UUID categoryB = UUID.randomUUID();
        UUID creatorA = UUID.randomUUID();
        UUID creatorB = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", likedContentId.toString()), Map.of("content_id", "bad-uuid")));
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", savedAndBrowsedContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", sharedContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", savedAndBrowsedContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", masteredContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of(
                    "lesson_id", lessonId.toString(),
                    "status", "in_progress",
                    "progress_percentage", 45,
                    "last_accessed_at", OffsetDateTime.now().minusHours(1).toString()
                ),
                Map.of("lesson_id", "bad-lesson", "status", "completed", "progress_percentage", 100)
            ));
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("query", "Slang!!! clip"),
                Map.of("query", "meme lore")
            ));
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("content_id", likedContentId.toString(), "created_at", OffsetDateTime.now().minusMinutes(20).toString()),
                Map.of("content_id", likedContentId.toString(), "created_at", OffsetDateTime.now().minusMinutes(5).toString()),
                Map.of("content_id", "bad-uuid", "created_at", "bad-date")
            ));
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("id", likedContentId.toString(), "category_id", categoryA.toString(), "creator_id", creatorA.toString()),
                Map.of("id", savedAndBrowsedContentId.toString(), "category_id", categoryA.toString(), "creator_id", creatorB.toString()),
                Map.of("id", sharedContentId.toString(), "category_id", categoryB.toString(), "creator_id", creatorA.toString())
            ));
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("content_id", likedContentId.toString(), "tag", "Slang"),
                Map.of("content_id", savedAndBrowsedContentId.toString(), "tag", "meme"),
                Map.of("content_id", sharedContentId.toString(), "tag", "slang"),
                Map.of("content_id", sharedContentId.toString(), "tag", " ")
            ));

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
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

        // verify
        verify(supabaseAdminRestClient).getList(eq("content"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    /** Verifies affinity hydration is skipped entirely when the user has no tracked interactions. */
    @Test
    void loadSignals_ShouldSkipAffinityQueries_WhenThereAreNoInteractions() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertTrue(signals.tagAffinity().isEmpty());
        assertTrue(signals.categoryAffinity().isEmpty());
        assertTrue(signals.creatorAffinity().isEmpty());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    /** Verifies search tokenization drops blank and too-short terms. */
    @Test
    void loadSignals_ShouldReturnEmptyTokens_WhenSearchQueriesAreBlankOrTooShort() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("query", "  "),
                Map.of("query", "it an to"),
                Map.of("query", "ok")
            ));
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertTrue(signals.recentSearchTerms().isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("search_history"), anyString(), any(TypeReference.class));
    }

    /** Verifies tokenization keeps at most eight normalized terms per query. */
    @Test
    void loadSignals_ShouldLimitTokenCount_WhenSearchQueryHasManyTokens() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("query", "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo")));
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertEquals(8, signals.recentSearchTerms().size());
        assertEquals(List.of("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"), signals.recentSearchTerms());

        // verify
        verify(supabaseAdminRestClient).getList(eq("search_history"), anyString(), any(TypeReference.class));
    }

    /** Verifies invalid progress values and timestamps fall back to safe defaults. */
    @Test
    void loadSignals_ShouldDefaultProgressAndTimestamp_WhenLessonProgressMetadataIsInvalid() {
        // arrange
        UUID lessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "lesson_id", lessonId.toString(),
                "status", "completed",
                "progress_percentage", "oops",
                "last_accessed_at", "bad-date"
            )));
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertEquals(0, signals.lessonProgressByLessonId().get(lessonId).progressPercentage());
        assertNull(signals.lessonProgressByLessonId().get(lessonId).lastAccessedAt());

        // verify
        verify(supabaseAdminRestClient).getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class));
    }

    /** Verifies invalid affinity rows are ignored while valid rows still contribute weight. */
    @Test
    void loadSignals_ShouldSkipInvalidAffinityRows_WhenContentAndTagRowsContainBadIds() {
        // arrange
        UUID likedContentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", likedContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("id", likedContentId.toString(), "category_id", categoryId.toString(), "creator_id", creatorId.toString()),
                Map.of("id", "bad-id", "category_id", categoryId.toString(), "creator_id", creatorId.toString())
            ));
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("content_id", likedContentId.toString(), "tag", "slang"),
                Map.of("content_id", "bad-id", "tag", "meme"),
                Map.of("content_id", likedContentId.toString(), "tag", " ")
            ));

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertEquals(3, signals.tagAffinity().get("slang"));
        assertEquals(3, signals.categoryAffinity().get(categoryId));
        assertEquals(3, signals.creatorAffinity().get(creatorId));

        // verify
        verify(supabaseAdminRestClient).getList(eq("content"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    /** Verifies missing impression tables are ignored while the schema rollout catches up. */
    @Test
    void loadSignals_ShouldIgnoreMissingImpressionTable_WhenSchemaIsStillRollingOut() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation_impressions does not exist"));

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertTrue(signals.recentImpressionCounts().isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class));
    }

    /** Verifies unexpected impression lookup failures are still propagated. */
    @Test
    void loadSignals_ShouldRethrowImpressionErrors_WhenTableFailureIsUnexpected() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "other failure"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationSignalService.loadSignals(userId)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient).getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class));
    }

    /** Verifies null impression error reasons are also treated as unexpected failures. */
    @Test
    void loadSignals_ShouldTreatMissingReasonAsNonMissingImpressionTableError_WhenReasonIsNull() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationSignalService.loadSignals(userId)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient).getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class));
    }

    /** Verifies null ids, string progress values, null timestamps, and null tags are normalized safely. */
    @Test
    void loadSignals_ShouldParseMixedLessonAndTagMetadata_WhenRowsContainNullAndStringValues() {
        // arrange
        UUID likedContentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID fallbackLessonId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Map<String, Object> nullLikeRow = new java.util.LinkedHashMap<>();
        nullLikeRow.put("content_id", null);
        Map<String, Object> stringProgressRow = new java.util.LinkedHashMap<>();
        stringProgressRow.put("lesson_id", lessonId.toString());
        stringProgressRow.put("status", "in_progress");
        stringProgressRow.put("progress_percentage", "42");
        stringProgressRow.put("last_accessed_at", null);
        Map<String, Object> nullProgressRow = new java.util.LinkedHashMap<>();
        nullProgressRow.put("lesson_id", fallbackLessonId.toString());
        nullProgressRow.put("status", "completed");
        nullProgressRow.put("progress_percentage", null);
        nullProgressRow.put("last_accessed_at", null);
        Map<String, Object> nullTagRow = new java.util.LinkedHashMap<>();
        nullTagRow.put("content_id", likedContentId.toString());
        nullTagRow.put("tag", null);

        when(supabaseAdminRestClient.getList(eq("content_likes"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(nullLikeRow, Map.of("content_id", likedContentId.toString())));
        when(supabaseAdminRestClient.getList(eq("content_saves"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_shares"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("browsing_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_concepts_mastered"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("user_lesson_progress"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(stringProgressRow, nullProgressRow));
        when(supabaseAdminRestClient.getList(eq("search_history"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("recommendation_impressions"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", likedContentId.toString(), "category_id", categoryId.toString(), "creator_id", creatorId.toString())));
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(nullTagRow, Map.of("content_id", likedContentId.toString(), "tag", " slang ")));

        // act
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);

        // assert
        assertEquals(Set.of(likedContentId), signals.likedContentIds());
        assertEquals(42, signals.lessonProgressByLessonId().get(lessonId).progressPercentage());
        assertEquals(0, signals.lessonProgressByLessonId().get(fallbackLessonId).progressPercentage());
        assertNull(signals.lessonProgressByLessonId().get(lessonId).lastAccessedAt());
        assertEquals(3, signals.tagAffinity().get("slang"));

        // verify
        verify(supabaseAdminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }
}



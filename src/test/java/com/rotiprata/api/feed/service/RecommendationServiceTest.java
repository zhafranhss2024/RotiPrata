package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentEngagementService;
import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkedLesson;
import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkSource;
import com.rotiprata.api.feed.service.RecommendationScorer.ScoredRecommendation;
import com.rotiprata.api.zdto.FeedResponse;
import com.rotiprata.api.zdto.RecommendationResponse;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @Mock
    private ContentEngagementService contentEngagementService;

    @Mock
    private ContentCreatorEnrichmentService contentCreatorEnrichmentService;

    @Mock
    private RecommendationSignalService recommendationSignalService;

    @Mock
    private ContentLessonLinkService contentLessonLinkService;

    private RecommendationService recommendationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
            supabaseAdminRestClient,
            contentEngagementService,
            contentCreatorEnrichmentService,
            recommendationSignalService,
            contentLessonLinkService,
            new RecommendationScorer()
        );
        userId = UUID.randomUUID();
    }

    /** Verifies the feed endpoint rejects blank access tokens before any recommendation work starts. */
    @Test
    void getFeed_ShouldReturnUnauthorized_WhenAccessTokenIsBlank() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(userId, " ", null, 1)
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies the explore endpoint rejects blank access tokens before any recommendation work starts. */
    @Test
    void getRecommendations_ShouldReturnUnauthorized_WhenAccessTokenIsBlank() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(userId, " ", 1)
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies the feed endpoint rejects requests when the authenticated user is missing. */
    @Test
    void getFeed_ShouldReturnUnauthorized_WhenUserIsMissing() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(null, "token", null, 1)
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies the explore endpoint rejects requests when the authenticated user is missing. */
    @Test
    void getRecommendations_ShouldReturnUnauthorized_WhenUserIsMissing() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(null, "token", 1)
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies malformed Base64 cursors are rejected before ranking runs. */
    @Test
    void getFeed_ShouldRejectCursor_WhenCursorIsNotBase64() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(userId, "token", "not-base64", 1)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies decoded cursors must contain score, timestamp, and content id parts. */
    @Test
    void getFeed_ShouldRejectCursor_WhenDecodedPayloadHasWrongParts() {
        // arrange
        String invalidCursor = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("10.0|2026-04-06T10:15:30Z".getBytes(StandardCharsets.UTF_8));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(userId, "token", invalidCursor, 1)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verifyNoInteractions(supabaseAdminRestClient, recommendationSignalService, contentLessonLinkService);
    }

    /** Verifies the explore endpoint short-circuits when both candidate queries return no rows. */
    @Test
    void getRecommendations_ShouldReturnEmpty_WhenCandidatePoolIsEmpty() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", null);

        // assert
        assertTrue(response.items().isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class));
        verify(supabaseAdminRestClient).getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class));
        verifyNoInteractions(recommendationSignalService, contentLessonLinkService, contentEngagementService, contentCreatorEnrichmentService);
    }

    /** Verifies the feed endpoint falls back to the default page size when no limit is provided. */
    @Test
    void getFeed_ShouldUseDefaultFeedLimit_WhenLimitIsNull() {
        // arrange
        List<Map<String, Object>> recentRows = buildCandidates(25, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        FeedResponse response = recommendationService.getFeed(userId, "token", null, null);

        // assert
        assertEquals(20, response.items().size());
        assertTrue(response.hasMore());
        assertNotNull(response.nextCursor());

        // verify
        verify(recommendationSignalService).loadSignals(userId);
        verify(contentLessonLinkService).resolveLinkedLessons(any());
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the explore endpoint falls back to the default size when a negative limit is provided. */
    @Test
    void getRecommendations_ShouldUseDefaultRecommendationLimit_WhenLimitIsNegative() {
        // arrange
        List<Map<String, Object>> recentRows = buildCandidates(30, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", -1);

        // assert
        assertEquals(24, response.items().size());

        // verify
        verify(recommendationSignalService).loadSignals(userId);
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the feed endpoint caps oversized limits at the configured maximum. */
    @Test
    void getFeed_ShouldCapFeedLimit_WhenLimitExceedsMaximum() {
        // arrange
        List<Map<String, Object>> recentRows = buildCandidates(60, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        FeedResponse response = recommendationService.getFeed(userId, "token", null, 999);

        // assert
        assertEquals(50, response.items().size());
        assertTrue(response.hasMore());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the explore endpoint caps oversized limits at the configured maximum. */
    @Test
    void getRecommendations_ShouldCapRecommendationLimit_WhenLimitExceedsMaximum() {
        // arrange
        List<Map<String, Object>> recentRows = buildCandidates(60, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 999);

        // assert
        assertEquals(48, response.items().size());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies feed pagination advances with a stable cursor and does not duplicate items across pages. */
    @Test
    void getFeed_ShouldPaginateWithoutDuplicatingItems_WhenCursorAdvances() {
        // arrange
        List<Map<String, Object>> recentRows = List.of(
            candidate(UUID.fromString("00000000-0000-0000-0000-000000000003"), OffsetDateTime.parse("2026-04-05T09:00:00Z")),
            candidate(UUID.fromString("00000000-0000-0000-0000-000000000002"), OffsetDateTime.parse("2026-04-04T09:00:00Z")),
            candidate(UUID.fromString("00000000-0000-0000-0000-000000000001"), OffsetDateTime.parse("2026-04-03T09:00:00Z"))
        );
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows, recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of(), List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of(), List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals(), emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of(), Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of(), List.of());

        // act
        FeedResponse firstPage = recommendationService.getFeed(userId, "token", null, 1);
        FeedResponse secondPage = recommendationService.getFeed(userId, "token", firstPage.nextCursor(), 1);

        // assert
        assertEquals(1, firstPage.items().size());
        assertEquals(1, secondPage.items().size());
        assertNotNull(firstPage.nextCursor());
        assertNotEquals(firstPage.items().get(0).get("id"), secondPage.items().get(0).get("id"));

        // verify
        verify(supabaseAdminRestClient, times(2)).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies feed pagination returns an empty page and no next cursor when all items are already behind the cursor. */
    @Test
    void getFeed_ShouldReturnNoCursor_WhenPageIsEmpty() {
        // arrange
        UUID contentId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-05T09:00:00Z");
        List<Map<String, Object>> recentRows = List.of(candidate(contentId, createdAt));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());

        String cursor = encodeCursor(17.6, createdAt, contentId);

        // act
        FeedResponse response = recommendationService.getFeed(userId, "token", cursor, 10);

        // assert
        assertTrue(response.items().isEmpty());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());

        // verify
        verify(supabaseAdminRestClient, never()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the feed endpoint returns all remaining items without fabricating another page cursor. */
    @Test
    void getFeed_ShouldReturnRemainingItemsWithoutHasMore_WhenCandidatesDoNotExceedLimit() {
        // arrange
        List<Map<String, Object>> recentRows = buildCandidates(2, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        FeedResponse response = recommendationService.getFeed(userId, "token", null, 5);

        // assert
        assertEquals(2, response.items().size());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the private cursor comparison returns the null-created-at branch result. */
    @Test
    void getFeed_ShouldHandleCursorComparison_WhenItemCreatedAtIsNull() {
        // arrange
        UUID contentId = UUID.randomUUID();
        OffsetDateTime cursorCreatedAt = OffsetDateTime.parse("2026-04-05T09:00:00Z");
        ScoredRecommendation item = new ScoredRecommendation(candidate(contentId, null), 12.0, null, contentId);

        // act
        int comparison = invokeCompareToCursor(item, 12.0, cursorCreatedAt, contentId);

        // assert
        assertEquals(-1, comparison);

        // verify
        assertDoesNotThrow(() -> invokeCompareToCursor(item, 12.0, cursorCreatedAt, contentId));
    }

    /** Verifies items with invalid ids are treated as cursor-equal and excluded from later pages. */
    @Test
    void getFeed_ShouldHandleCursorComparison_WhenContentIdsAreNull() {
        // arrange
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-05T09:00:00Z");
        List<Map<String, Object>> recentRows = List.of(
            candidate(firstId, createdAt),
            candidate("not-a-uuid", createdAt, "https://cdn.example.com/invalid.mp4")
        );
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(recentRows, recentRows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of(), List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of(), List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals(), emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(new LinkedHashMap<>(), new LinkedHashMap<>());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        FeedResponse firstPage = recommendationService.getFeed(userId, "token", null, 1);
        FeedResponse secondPage = recommendationService.getFeed(userId, "token", firstPage.nextCursor(), 5);

        // assert
        assertEquals(1, firstPage.items().size());
        assertTrue(secondPage.items().isEmpty());

        // verify
        verify(supabaseAdminRestClient, times(1)).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies the private cursor comparison branch treats null cursor timestamps as older than concrete items. */
    @Test
    void getFeed_ShouldHandleCursorComparison_WhenCursorCreatedAtIsNull() {
        // arrange
        UUID contentId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-05T09:00:00Z");
        ScoredRecommendation item = new ScoredRecommendation(candidate(contentId, createdAt), 12.0, createdAt, contentId);

        // act
        int comparison = invokeCompareToCursor(item, 12.0, null, contentId);

        // assert
        assertEquals(1, comparison);

        // verify
        assertDoesNotThrow(() -> invokeCompareToCursor(item, 12.0, null, contentId));
    }

    /** Verifies differing timestamps return the created-at comparison branch from the cursor comparator. */
    @Test
    void getFeed_ShouldCompareCreatedAt_WhenCursorTimestampDiffersFromItemTimestamp() {
        // arrange
        UUID contentId = UUID.randomUUID();
        OffsetDateTime itemCreatedAt = OffsetDateTime.parse("2026-04-05T09:00:00Z");
        OffsetDateTime cursorCreatedAt = OffsetDateTime.parse("2026-04-04T09:00:00Z");
        ScoredRecommendation item = new ScoredRecommendation(candidate(contentId, itemCreatedAt), 12.0, itemCreatedAt, contentId);

        // act
        int comparison = invokeCompareToCursor(item, 12.0, cursorCreatedAt, contentId);

        // assert
        assertEquals(-1, comparison);

        // verify
        assertDoesNotThrow(() -> invokeCompareToCursor(item, 12.0, cursorCreatedAt, contentId));
    }

    /** Verifies schema-lag errors retry the content query without the media status filter. */
    @Test
    void getRecommendations_ShouldRetryWithoutMediaStatus_WhenSchemaErrorContainsMediaStatus() {
        // arrange
        AtomicInteger fallbackQueries = new AtomicInteger();
        List<Map<String, Object>> rows = buildCandidates(2, 0);
        when(supabaseAdminRestClient.getList(eq("content"), any(), any(TypeReference.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(1);
                if (query.contains("media_status=eq.ready")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pgrst204 media_status");
                }
                fallbackQueries.incrementAndGet();
                return rows;
            });
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 2);

        // assert
        assertEquals(2, response.items().size());
        assertEquals(2, fallbackQueries.get());

        // verify
        verify(supabaseAdminRestClient, times(4)).getList(eq("content"), any(), any(TypeReference.class));
    }

    /** Verifies non-schema content fetch failures are propagated immediately. */
    @Test
    void getRecommendations_ShouldRethrow_WhenContentFetchFailsForNonMediaStatusReason() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content"), any(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "different failure"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(userId, "token", 2)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_tags"), any(), any(TypeReference.class));
        verifyNoInteractions(recommendationSignalService, contentLessonLinkService, contentEngagementService, contentCreatorEnrichmentService);
    }

    /** Verifies null content fetch reasons do not trigger the media-status fallback path. */
    @Test
    void getRecommendations_ShouldRethrow_WhenContentFetchFailsWithoutReason() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content"), any(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(userId, "token", 2)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("content"), any(), any(TypeReference.class));
    }

    /** Verifies the merged candidate pool keeps only the first copy of each content id. */
    @Test
    void getRecommendations_ShouldDeduplicateCandidatePool_WhenRecentAndPopularQueriesOverlap() {
        // arrange
        UUID sharedId = UUID.randomUUID();
        UUID uniqueId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(List.of(candidate(sharedId, OffsetDateTime.parse("2026-04-05T09:00:00Z"))));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of(
                candidate(sharedId, OffsetDateTime.parse("2026-04-04T09:00:00Z")),
                candidate(uniqueId, OffsetDateTime.parse("2026-04-03T09:00:00Z"))
            ));
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 10);

        // assert
        assertEquals(2, response.items().size());

        // verify
        verify(contentLessonLinkService).resolveLinkedLessons(argThat(ids -> ids.size() == 2 && ids.contains(sharedId) && ids.contains(uniqueId)));
    }

    /** Verifies invalid candidate ids skip tag lookups while still allowing ranking and hydration. */
    @Test
    void getRecommendations_ShouldSkipTagAttachment_WhenCandidateIdsAreInvalid() {
        // arrange
        List<Map<String, Object>> rows = List.of(
            candidate("not-a-uuid", OffsetDateTime.parse("2026-04-05T09:00:00Z"), "https://cdn.example.com/a.mp4"),
            candidate("still-not-a-uuid", OffsetDateTime.parse("2026-04-04T09:00:00Z"), "https://cdn.example.com/b.mp4")
        );
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(Set.of())).thenReturn(new LinkedHashMap<>());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 5);

        // assert
        assertEquals(2, response.items().size());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_tags"), any(), any(TypeReference.class));
        verify(contentLessonLinkService).resolveLinkedLessons(Set.of());
        verify(supabaseAdminRestClient, never()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies impression logging is skipped when ranked items do not have valid content ids. */
    @Test
    void getRecommendations_ShouldSkipImpressionLogging_WhenRankedItemsHaveNullContentIds() {
        // arrange
        List<Map<String, Object>> rows = List.of(candidate("not-a-uuid", OffsetDateTime.parse("2026-04-05T09:00:00Z"), "https://cdn.example.com/no-id.mp4"));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(Set.of())).thenReturn(new LinkedHashMap<>());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertEquals(1, response.items().size());

        // verify
        verify(supabaseAdminRestClient, never()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies missing impression-table errors are ignored so recommendation delivery still succeeds. */
    @Test
    void getRecommendations_ShouldIgnoreMissingImpressionsTable_WhenRolloutTableIsMissing() {
        // arrange
        List<Map<String, Object>> rows = buildCandidates(2, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation_impressions does not exist"));

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 2);

        // assert
        assertEquals(2, response.items().size());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies unexpected impression insert failures are still surfaced to the caller. */
    @Test
    void getRecommendations_ShouldRethrow_WhenImpressionInsertFailsForUnexpectedReason() {
        // arrange
        List<Map<String, Object>> rows = buildCandidates(2, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "insert failed"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(userId, "token", 2)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies missing impression-table reasons are not suppressed when the exception reason is null. */
    @Test
    void getRecommendations_ShouldRethrow_WhenImpressionInsertFailsWithoutReason() {
        // arrange
        List<Map<String, Object>> rows = buildCandidates(2, 0);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(userId, "token", 2)
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    /** Verifies HLS media urls are surfaced as stream metadata during hydration. */
    @Test
    void getRecommendations_ShouldSetHlsStreamFields_WhenMediaUrlUsesPlaylist() {
        // arrange
        List<Map<String, Object>> rows = List.of(candidate(UUID.randomUUID(), OffsetDateTime.parse("2026-04-05T09:00:00Z"), "https://cdn.example.com/video.m3u8"));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertEquals("https://cdn.example.com/video.m3u8", response.items().get(0).get("stream_url"));
        assertEquals("hls", response.items().get(0).get("stream_type"));

        // verify
        verify(contentCreatorEnrichmentService).enrichWithCreatorProfiles(any());
    }

    /** Verifies regular media files are marked with the file stream type during hydration. */
    @Test
    void getRecommendations_ShouldSetFileStreamType_WhenMediaUrlIsNotHls() {
        // arrange
        List<Map<String, Object>> rows = List.of(candidate(UUID.randomUUID(), OffsetDateTime.parse("2026-04-05T09:00:00Z"), "https://cdn.example.com/video.mp4"));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertEquals("file", response.items().get(0).get("stream_type"));

        // verify
        verify(contentEngagementService).decorateItemsWithUserEngagement(any(), eq(userId), eq("token"));
    }

    /** Verifies blank media urls leave stream metadata unset during hydration. */
    @Test
    void getRecommendations_ShouldLeaveStreamFieldsUnset_WhenMediaUrlIsBlank() {
        // arrange
        List<Map<String, Object>> rows = List.of(candidate(UUID.randomUUID(), OffsetDateTime.parse("2026-04-05T09:00:00Z"), "   "));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(rows);
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any())).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertFalse(response.items().get(0).containsKey("stream_url"));
        assertFalse(response.items().get(0).containsKey("stream_type"));

        // verify
        verify(contentCreatorEnrichmentService).enrichWithCreatorProfiles(any());
    }

    /** Verifies linked lesson metadata is passed to the scorer pipeline for recommendation ranking. */
    @Test
    void getRecommendations_ShouldResolveLinkedLessons_WhenCandidateIdsAreValid() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        Map<UUID, List<LinkedLesson>> linkedLessons = Map.of(
            contentId,
            List.of(new LinkedLesson(lessonId, "Lesson One", UUID.randomUUID(), LinkSource.LESSON_CONCEPT))
        );
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(List.of(candidate(contentId, OffsetDateTime.parse("2026-04-05T09:00:00Z"))));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(Set.of(contentId))).thenReturn(linkedLessons);
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertEquals(1, response.items().size());

        // verify
        verify(contentLessonLinkService).resolveLinkedLessons(Set.of(contentId));
    }

    /** Verifies tag hydration keeps valid normalized tags and skips rows with null or blank tag metadata. */
    @Test
    void getRecommendations_ShouldAttachNormalizedTags_WhenTagRowsContainMixedValidity() {
        // arrange
        UUID contentId = UUID.randomUUID();
        Map<String, Object> nullTagRow = new LinkedHashMap<>();
        nullTagRow.put("content_id", contentId.toString());
        nullTagRow.put("tag", null);
        Map<String, Object> nullContentRow = new LinkedHashMap<>();
        nullContentRow.put("content_id", null);
        nullContentRow.put("tag", "ignored");
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(List.of(candidate(contentId, OffsetDateTime.parse("2026-04-05T09:00:00Z"))));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("content_id", contentId.toString(), "tag", " Slang "),
                Map.of("content_id", contentId.toString(), "tag", " "),
                nullTagRow,
                nullContentRow
            ));
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(Set.of(contentId))).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertEquals(List.of("slang"), response.items().get(0).get("tags"));

        // verify
        verify(supabaseAdminRestClient).getList(eq("content_tags"), any(), any(TypeReference.class));
    }

    /** Verifies hydration tolerates null enriched entries and leaves them untouched. */
    @Test
    void getRecommendations_ShouldReturnNullEnrichedItems_WhenEnrichmentProducesNullRows() {
        // arrange
        UUID contentId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=160")), any(TypeReference.class)))
            .thenReturn(List.of(candidate(contentId, OffsetDateTime.parse("2026-04-05T09:00:00Z"))));
        when(supabaseAdminRestClient.getList(eq("content"), argThat(query -> query.contains("limit=80")), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content_tags"), any(), any(TypeReference.class))).thenReturn(List.of());
        when(recommendationSignalService.loadSignals(userId)).thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(Set.of(contentId))).thenReturn(Map.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenReturn(java.util.Arrays.asList((Map<String, Object>) null));
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // act
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        // assert
        assertNull(response.items().get(0));

        // verify
        verify(contentCreatorEnrichmentService).enrichWithCreatorProfiles(any());
    }

    /** Verifies private UUID parsing returns null when recommendation metadata is absent. */
    @Test
    void parseUuid_ShouldReturnNull_WhenValueIsNull() {
        // arrange

        // act
        UUID parsed = invokeParseUuid(null);

        // assert
        assertNull(parsed);

        // verify
        assertDoesNotThrow(() -> invokeParseUuid(null));
    }

    private RecommendationSignals emptySignals() {
        return new RecommendationSignals(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of()
        );
    }

    private List<Map<String, Object>> buildCandidates(int count, int offsetDays) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(candidate(
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-04-05T09:00:00Z").minusDays(offsetDays).minusMinutes(index)
            ));
        }
        return rows;
    }

    private String encodeCursor(double score, OffsetDateTime createdAt, UUID contentId) {
        String payload = score + "|" + createdAt + "|" + contentId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private int invokeCompareToCursor(ScoredRecommendation item, double score, OffsetDateTime createdAt, UUID contentId) {
        try {
            Class<?> cursorClass = Class.forName("com.rotiprata.api.feed.service.RecommendationService$RecommendationCursor");
            Constructor<?> constructor = cursorClass.getDeclaredConstructor(double.class, OffsetDateTime.class, UUID.class);
            constructor.setAccessible(true);
            Object cursor = constructor.newInstance(score, createdAt, contentId);
            Method method = RecommendationService.class.getDeclaredMethod("compareToCursor", ScoredRecommendation.class, cursorClass);
            method.setAccessible(true);
            return (Integer) method.invoke(recommendationService, item, cursor);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private UUID invokeParseUuid(Object value) {
        try {
            Method method = RecommendationService.class.getDeclaredMethod("parseUuid", Object.class);
            method.setAccessible(true);
            return (UUID) method.invoke(recommendationService, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private Map<String, Object> candidate(UUID contentId, OffsetDateTime createdAt) {
        return candidate(contentId, createdAt, "https://cdn.example.com/" + contentId + ".mp4");
    }

    private Map<String, Object> candidate(Object contentId, OffsetDateTime createdAt, String mediaUrl) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("id", contentId == null ? null : contentId.toString());
        candidate.put("creator_id", UUID.randomUUID().toString());
        candidate.put("title", "Video " + contentId);
        candidate.put("description", "Test video");
        candidate.put("content_type", "video");
        candidate.put("media_url", mediaUrl);
        candidate.put("thumbnail_url", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
        candidate.put("category_id", UUID.randomUUID().toString());
        candidate.put("status", "approved");
        candidate.put("learning_objective", "Learn something new");
        candidate.put("origin_explanation", "origin");
        candidate.put("definition_literal", "literal");
        candidate.put("definition_used", "used");
        candidate.put("older_version_reference", "older");
        candidate.put("educational_value_votes", 0);
        candidate.put("view_count", 100);
        candidate.put("is_featured", false);
        candidate.put("reviewed_by", UUID.randomUUID().toString());
        candidate.put("reviewed_at", createdAt == null ? null : createdAt.toString());
        candidate.put("review_feedback", "ok");
        candidate.put("created_at", createdAt == null ? null : createdAt.toString());
        candidate.put("updated_at", createdAt == null ? null : createdAt.toString());
        candidate.put("is_submitted", true);
        candidate.put("media_status", "ready");
        candidate.put("likes_count", 10);
        candidate.put("comments_count", 2);
        candidate.put("saves_count", 4);
        candidate.put("shares_count", 1);
        return candidate;
    }
}

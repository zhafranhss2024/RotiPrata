package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentEngagementService;
import com.rotiprata.api.zdto.FeedResponse;
import com.rotiprata.api.zdto.RecommendationResponse;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private List<Map<String, Object>> candidates;

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
        candidates = List.of(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(1)),
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1))
        );
        stubRecommendationDependencies(candidates);
    }

    @Test
    void getFeed_shouldPaginateWithoutDuplicatingItems() {
        FeedResponse firstPage = recommendationService.getFeed(userId, "token", null, 1);
        FeedResponse secondPage = recommendationService.getFeed(userId, "token", firstPage.nextCursor(), 1);

        assertEquals(1, firstPage.items().size());
        assertEquals(1, secondPage.items().size());
        assertNotNull(firstPage.nextCursor());
        assertNotEquals(firstPage.items().get(0).get("id"), secondPage.items().get(0).get("id"));
        verify(supabaseAdminRestClient, atLeastOnce()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    @Test
    void getRecommendations_shouldReturnRecommendedItems() {
        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 2);

        assertEquals(2, response.items().size());
        verify(supabaseAdminRestClient, atLeastOnce()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    @Test
    void getFeed_shouldRejectMissingAccessToken() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(userId, null, null, 1)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getRecommendations_shouldRejectMissingUser() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getRecommendations(null, "token", 1)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getFeed_shouldRejectInvalidCursor() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> recommendationService.getFeed(userId, "token", "not-base64", 1)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getRecommendations_shouldReturnEmptyWhenCandidatePoolIsEmpty() {
        stubRecommendationDependencies(List.of());

        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", null);

        assertTrue(response.items().isEmpty());
        verify(recommendationSignalService, never()).loadSignals(userId);
        verify(contentLessonLinkService, never()).resolveLinkedLessons(any());
        verify(supabaseAdminRestClient, never()).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));
    }

    @Test
    void getFeed_shouldApplyDefaultAndMaximumLimits() {
        stubRecommendationDependencies(buildCandidates(60));

        FeedResponse defaultResponse = recommendationService.getFeed(userId, "token", null, 0);
        FeedResponse cappedResponse = recommendationService.getFeed(userId, "token", null, 999);

        assertEquals(20, defaultResponse.items().size());
        assertTrue(defaultResponse.hasMore());
        assertEquals(50, cappedResponse.items().size());
        assertTrue(cappedResponse.hasMore());
    }

    @Test
    void getRecommendations_shouldApplyDefaultAndMaximumLimits() {
        stubRecommendationDependencies(buildCandidates(60));

        RecommendationResponse defaultResponse = recommendationService.getRecommendations(userId, "token", null);
        RecommendationResponse cappedResponse = recommendationService.getRecommendations(userId, "token", 999);

        assertEquals(24, defaultResponse.items().size());
        assertEquals(48, cappedResponse.items().size());
    }

    @Test
    void getRecommendations_shouldRetryWithoutMediaStatusWhenSchemaLags() {
        AtomicInteger fallbackQueries = new AtomicInteger();

        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(1);
                if (query.contains("media_status=eq.ready")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pgrst204 media_status");
                }
                fallbackQueries.incrementAndGet();
                return candidates;
            });
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of());

        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 2);

        assertEquals(2, response.items().size());
        assertEquals(2, fallbackQueries.get());
        verify(supabaseAdminRestClient, atLeastOnce()).getList(
            eq("content"),
            argThat(query -> !query.contains("media_status=eq.ready")),
            any(TypeReference.class)
        );
    }

    @Test
    void getRecommendations_shouldIgnoreMissingImpressionTableDuringLogging() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation_impressions does not exist"))
            .when(supabaseAdminRestClient).postList(eq("recommendation_impressions"), any(), any(TypeReference.class));

        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 2);

        assertEquals(2, response.items().size());
    }

    @Test
    void getRecommendations_shouldAttachStreamFieldsDuringHydration() {
        stubRecommendationDependencies(List.of(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(2), "https://cdn.example.com/video.m3u8")
        ));

        RecommendationResponse response = recommendationService.getRecommendations(userId, "token", 1);

        assertEquals("https://cdn.example.com/video.m3u8", response.items().get(0).get("stream_url"));
        assertEquals("hls", response.items().get(0).get("stream_type"));
    }

    private void stubRecommendationDependencies(List<Map<String, Object>> contentRows) {
        candidates = contentRows;
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(contentRows);
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of());
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(recommendationSignalService.loadSignals(userId))
            .thenReturn(emptySignals());
        when(contentLessonLinkService.resolveLinkedLessons(any()))
            .thenReturn(Map.of());
        when(supabaseAdminRestClient.postList(eq("recommendation_impressions"), any(), any(TypeReference.class)))
            .thenReturn(List.of());
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

    private List<Map<String, Object>> buildCandidates(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(candidate(UUID.randomUUID(), OffsetDateTime.now().minusMinutes(index + 1L)));
        }
        return rows;
    }

    private Map<String, Object> candidate(UUID contentId, OffsetDateTime createdAt) {
        return candidate(contentId, createdAt, "https://cdn.example.com/" + contentId + ".mp4");
    }

    private Map<String, Object> candidate(UUID contentId, OffsetDateTime createdAt, String mediaUrl) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("id", contentId.toString());
        candidate.put("creator_id", UUID.randomUUID().toString());
        candidate.put("title", "Video " + contentId);
        candidate.put("description", "Test video");
        candidate.put("content_type", "video");
        candidate.put("media_url", mediaUrl);
        candidate.put("thumbnail_url", "https://cdn.example.com/" + contentId + ".jpg");
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
        candidate.put("reviewed_at", createdAt.toString());
        candidate.put("review_feedback", "ok");
        candidate.put("created_at", createdAt.toString());
        candidate.put("updated_at", createdAt.toString());
        candidate.put("is_submitted", true);
        candidate.put("media_status", "ready");
        candidate.put("likes_count", 10);
        candidate.put("comments_count", 2);
        candidate.put("saves_count", 4);
        candidate.put("shares_count", 1);
        return candidate;
    }
}

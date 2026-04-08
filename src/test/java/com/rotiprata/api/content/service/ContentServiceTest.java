package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.content.dto.ContentCommentCreateRequest;
import com.rotiprata.api.content.dto.ContentFlagRequest;
import com.rotiprata.api.content.dto.ContentPlaybackEventRequest;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;
    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;
    @Mock
    private ContentEngagementService contentEngagementService;
    @Mock
    private ContentCreatorEnrichmentService contentCreatorEnrichmentService;
    @Mock
    private UserService userService;

    private ContentService service;
    private UUID userId;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        service = new ContentService(
            supabaseRestClient,
            supabaseAdminRestClient,
            contentEngagementService,
            contentCreatorEnrichmentService,
            userService
        );
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
    }

    // Ensures missing tokens are rejected before any data call is made.
    @Test
    void getContentById_ShouldThrowUnauthorized_WhenAccessTokenIsMissing() {
        //arrange

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.getContentById(userId, contentId, " "));

        //assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, never()).getList(anyString(), anyString(), anyString(), any(TypeReference.class));
    }

    // Ensures content payload includes tags and stream metadata for playable media.
    @Test
    @SuppressWarnings("unchecked")
    void getContentById_ShouldReturnHydratedItem_WhenApprovedContentExists() {
        //arrange
        when(supabaseRestClient.getList(eq("content"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.m3u8")));
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.m3u8"))));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.m3u8"))));
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("content_id=eq."), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("tag", "food"), Map.of("tag", " culture ")));

        //act
        Map<String, Object> result = service.getContentById(userId, contentId, "token");

        //assert
        assertEquals(contentId.toString(), result.get("id"));
        assertEquals("hls", result.get("stream_type"));
        assertEquals("https://cdn/video.m3u8", result.get("stream_url"));
        assertEquals(List.of("food", "culture"), result.get("tags"));

        //verify
        verify(contentEngagementService).decorateItemsWithUserEngagement(any(), eq(userId), eq("token"));
        verify(contentCreatorEnrichmentService).enrichWithCreatorProfiles(any());
    }

    // Ensures owner can still retrieve content via admin fallback when not approved.
    @Test
    @SuppressWarnings("unchecked")
    void getContentById_ShouldFallbackToAdminLookup_WhenPublicLookupReturnsEmpty() {
        //arrange
        when(supabaseRestClient.getList(eq("content"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("content"), contains("creator_id=eq." + userId), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.mp4")));
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.mp4"))));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", contentId.toString(), "media_url", "https://cdn/video.mp4"))));
        when(supabaseAdminRestClient.getList(eq("content_tags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of());

        //act
        Map<String, Object> result = service.getContentById(userId, contentId, "token");

        //assert
        assertEquals("file", result.get("stream_type"));

        //verify
        verify(supabaseAdminRestClient).getList(eq("content"), contains("creator_id=eq." + userId), any(TypeReference.class));
    }

   // Ensures similar content uses tag overlap ordering and obeys requested limit.
    @Test
    @SuppressWarnings("unchecked")
    void getSimilarContent_ShouldReturnMatchedRows_WhenTagsAreShared() {
        // arrange
        String similarA = UUID.randomUUID().toString();
        String similarB = UUID.randomUUID().toString();

        // stub supabaseRestClient.getList for main content
        when(supabaseRestClient.getList(eq("content"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("id", contentId.toString(), "media_url", "https://cdn/current.m3u8"))
            )))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("id", similarA, "created_at", "2026-04-01T10:00:00Z", "media_url", "https://cdn/a.mp4")),
                new LinkedHashMap<>(Map.of("id", similarB, "created_at", "2026-04-03T10:00:00Z", "media_url", "https://cdn/b.mp4"))
            )));

        // stub contentEngagementService.decorateItemsWithUserEngagement
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of(
                    "id", contentId.toString(),
                    "media_url", "https://cdn/current.m3u8",
                    "tags", new ArrayList<>(List.of("spicy", "street"))
                ))
            )))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // stub contentCreatorEnrichmentService.enrichWithCreatorProfiles
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of(
                    "id", contentId.toString(),
                    "media_url", "https://cdn/current.m3u8",
                    "tags", new ArrayList<>(List.of("spicy", "street"))
                ))
            )))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // stub supabaseAdminRestClient.getList for tags of current content
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("content_id=eq."), any(TypeReference.class)))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("tag", "spicy")),
                new LinkedHashMap<>(Map.of("tag", "street"))
            )));

        // stub supabaseAdminRestClient.getList for tags of other content
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("not.eq." + contentId), any(TypeReference.class)))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("content_id", similarA, "tag", "spicy")),
                new LinkedHashMap<>(Map.of("content_id", similarA, "tag", "street")),
                new LinkedHashMap<>(Map.of("content_id", similarB, "tag", "spicy"))
            )));

        // stub supabaseAdminRestClient.getList for tags with "in" filter
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("in.("), any(TypeReference.class)))
            .thenReturn(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("content_id", similarA, "tag", "spicy")),
                new LinkedHashMap<>(Map.of("content_id", similarB, "tag", "street"))
            )));

        // act
        List<Map<String, Object>> result = service.getSimilarContent(userId, contentId, "token", 1);

        // assert
        assertEquals(1, result.size());
        assertEquals(similarA, result.get(0).get("id"));

        // verify
        verify(supabaseRestClient, times(2)).getList(eq("content"), anyString(), eq("token"), any(TypeReference.class));
    }

    // Ensures profile collection rejects unsupported collection names.
    @Test
    void getProfileContentCollection_ShouldThrowBadRequest_WhenCollectionIsInvalid() {
        //arrange

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.getProfileContentCollection(userId, "token", "archived"));

        //assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        //verify
        verify(supabaseAdminRestClient, never()).getList(anyString(), anyString(), any(TypeReference.class));
    }

    // Ensures search results merge title and tag hits while escaping unsafe query chars.
    @Test
    @SuppressWarnings("unchecked")
    void getFilteredContent_ShouldMergeTagMatches_WhenQueryIsProvided() {
        //arrange
        ContentSearchDTO titleHit = new ContentSearchDTO("c1", "video", "Roti", "x".repeat(130), null);
        ContentSearchDTO tagHit = new ContentSearchDTO("c2", "video", "Prata", "desc", null);
        when(supabaseRestClient.getList(eq("/content"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(titleHit));
        when(supabaseRestClient.getList(eq("/content_tags"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", "c2"), Map.of("content_id", "c2")));
        when(supabaseRestClient.getList(eq("/content"), contains("id=in.(c2)"), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(tagHit));

        //act
        List<ContentSearchDTO> result = service.getFilteredContent("roti(),", "video", "token");

        //assert
        assertEquals(2, result.size());
        assertTrue(result.get(0).snippet().endsWith("..."));

        //verify
        verify(supabaseRestClient).getList(eq("/content_tags"), contains("tag=ilike.*roti   *"), eq("token"), any(TypeReference.class));
    }

    // Ensures playback event failures are swallowed without surfacing API errors.
    @Test
    @SuppressWarnings("unchecked")
    void recordPlaybackEvent_ShouldSwallowException_WhenInsertFails() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad payload"))
            .when(supabaseAdminRestClient).postList(eq("content_playback_events"), any(), any(TypeReference.class));

        //act
        service.recordPlaybackEvent(
            userId,
            contentId,
            new ContentPlaybackEventRequest(100L, 1, 50L, 1000L, true, 0, "wifi", "ua")
        );

        //assert
        assertTrue(true);

        //verify
        verify(supabaseAdminRestClient).postList(eq("content_playback_events"), any(), any(TypeReference.class));
    }

    // Ensures duplicate-like errors are tolerated and still refresh engagement counters.
    @Test
    @SuppressWarnings("unchecked")
    void likeContent_ShouldRefreshCounts_WhenUniqueConstraintOccurs() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())))
            .thenReturn(List.of(Map.of("id", "1")))
            .thenReturn(List.of(Map.of("id", "1")))
            .thenReturn(List.of(Map.of("id", "1")))
            .thenReturn(List.of(Map.of("id", "1")))
            .thenReturn(List.of());
        when(supabaseRestClient.getList(eq("content_likes"), contains("limit=1"), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of());
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "duplicate key value violates unique constraint"))
            .when(supabaseRestClient).postList(eq("content_likes"), any(), eq("token"), any(TypeReference.class));

        //act
        service.likeContent(userId, contentId, "token");

        //assert
        assertTrue(true);

        //verify
        verify(supabaseAdminRestClient).patchList(eq("content"), contains("id=eq." + contentId), any(), any(TypeReference.class));
    }

    // Ensures flag creation blocks duplicate pending flags for the same user/content pair.
    @Test
    @SuppressWarnings("unchecked")
    void flagContent_ShouldThrowConflict_WhenPendingFlagAlreadyExists() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        when(supabaseRestClient.getList(eq("content_flags"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString())));

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.flagContent(userId, contentId, new ContentFlagRequest("Spam", "dup"), "token"));

        //assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, never()).postList(eq("content_flags"), any(), anyString(), any(TypeReference.class));
    }

    // Ensures comments list resolves author names and returns anonymous fallback when missing.
    @Test
    @SuppressWarnings("unchecked")
    void listComments_ShouldResolveAuthors_WhenCommentsExist() {
        //arrange
        UUID commenterId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        when(supabaseRestClient.getList(eq("content_comments"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "id", commentId.toString(),
                "content_id", contentId.toString(),
                "user_id", commenterId.toString(),
                "body", "Great!",
                "created_at", OffsetDateTime.parse("2026-04-01T10:00:00Z").toString(),
                "updated_at", OffsetDateTime.parse("2026-04-01T10:00:00Z").toString()
            )));
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("user_id", commenterId.toString(), "display_name", "Chef")));

        //act
        var result = service.listComments(userId, contentId, 10, 0, "token");

        //assert
        assertEquals(1, result.size());
        assertEquals("Chef", result.get(0).author());

        //verify
        verify(supabaseAdminRestClient).getList(eq("profiles"), anyString(), any(TypeReference.class));
    }

    // Ensures comment creation validates parent id existence before insertion.
    @Test
    @SuppressWarnings("unchecked")
    void createComment_ShouldThrowBadRequest_WhenParentCommentDoesNotExist() {
        //arrange
        UUID parentId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        when(supabaseRestClient.getList(eq("content_comments"), contains("id=eq." + parentId), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of());

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.createComment(userId, contentId, new ContentCommentCreateRequest("hello", parentId), "token"));

        //assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, never()).postList(eq("content_comments"), any(), anyString(), any(TypeReference.class));
    }

    // Ensures comment deletion is blocked for non-owner users without admin role.
    @Test
    @SuppressWarnings("unchecked")
    void deleteComment_ShouldThrowForbidden_WhenUserIsNotOwnerOrAdmin() {
        //arrange
        UUID commentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        when(supabaseAdminRestClient.getList(eq("content_comments"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", commentId.toString(), "user_id", ownerId.toString())));
        when(userService.getRoles(userId, "token")).thenReturn(List.of(AppRole.USER));

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.deleteComment(userId, contentId, commentId, "token"));

        //assert
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        //verify
        verify(supabaseAdminRestClient, never()).patchList(eq("content_comments"), anyString(), any(), any(TypeReference.class));
    }

    // Ensures media-status fallback retry occurs when schema mismatch error is returned.
    @Test
    @SuppressWarnings("unchecked")
    void getSimilarContent_ShouldRetryWithoutMediaStatus_WhenColumnErrorOccurs() {
        //arrange
        String similarId = UUID.randomUUID().toString();
        when(supabaseRestClient.getList(eq("content"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString(), "media_url", "https://cdn/current.m3u8")))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "column media_status does not exist"))
            .thenReturn(new java.util.ArrayList<>(List.of(Map.of("id", similarId, "created_at", "2026-04-03T10:00:00Z", "media_url", "https://cdn/a.mp4"))));
        when(contentEngagementService.decorateItemsWithUserEngagement(any(), eq(userId), eq("token")))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of(
                "id", contentId.toString(),
                "media_url", "https://cdn/current.m3u8",
                "tags", List.of("spicy")
            ))))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", similarId, "media_url", "https://cdn/a.mp4"))));
        when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any()))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of(
                "id", contentId.toString(),
                "media_url", "https://cdn/current.m3u8",
                "tags", List.of("spicy")
            ))))
            .thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of("id", similarId, "media_url", "https://cdn/a.mp4"))));
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("content_id=eq."), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("tag", "spicy")));
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("not.eq." + contentId), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", similarId, "tag", "spicy")));
        when(supabaseAdminRestClient.getList(eq("content_tags"), contains("in.("), any(TypeReference.class)))
            .thenReturn(List.of());

        //act
        List<Map<String, Object>> result = service.getSimilarContent(userId, contentId, "token", 3);

        //assert
        assertFalse(result.isEmpty());

        //verify
        verify(supabaseRestClient, times(3)).getList(eq("content"), anyString(), eq("token"), any(TypeReference.class));
    }

    // Ensures analytics helper delegates to admin client for monthly flagged content retrieval.
    @Test
    @SuppressWarnings("unchecked")
    void getFlaggedContentByMonthAndYear_ShouldReturnRows_WhenClientReturnsData() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", UUID.randomUUID().toString())));

        //act
        List<Map<String, Object>> result = service.getFlaggedContentByMonthAndYear("token", "04", "2026");

        //assert
        assertNotNull(result);
        assertEquals(1, result.size());

        //verify
        verify(supabaseAdminRestClient).getList(eq("content_flags"), anyString(), any(TypeReference.class));
    }
}
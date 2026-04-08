package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.domain.ContentMedia;
import com.rotiprata.api.content.domain.ContentType;
import com.rotiprata.api.content.dto.ContentSubmitRequest;
import com.rotiprata.api.content.dto.ContentUpdateRequest;
import com.rotiprata.application.MediaProcessingService;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentDraftServiceTest {

    @Mock
    private SupabaseAdminRestClient adminRestClient;
    @Mock
    private MediaProcessingService mediaProcessingService;

    // Ensures upload rejects text type because no media should be processed for text drafts.
    @Test
    @SuppressWarnings("unchecked")
    void startUpload_ShouldThrowBadRequest_WhenContentTypeIsText() {
        //arrange
        ContentDraftService service = new ContentDraftService(adminRestClient, mediaProcessingService);
        MockMultipartFile file = new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[] {1, 2});

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.startUpload(UUID.randomUUID(), ContentType.TEXT, file));

        //assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        //verify
        verify(adminRestClient, never()).postList(eq("content"), any(), any(TypeReference.class));
    }

    // Ensures upload workflow creates draft/media rows and starts async processing.
    @Test
    @SuppressWarnings("unchecked")
    void startUpload_ShouldReturnProcessingResponse_WhenValidUploadIsProvided() {
        //arrange
        ContentDraftService service = new ContentDraftService(adminRestClient, mediaProcessingService);
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content created = new Content();
        created.setId(contentId);
        when(adminRestClient.postList(eq("content"), any(), any(TypeReference.class))).thenReturn(List.of(created));
        when(adminRestClient.postList(eq("content_media"), any(), any(TypeReference.class))).thenReturn(List.of(new ContentMedia()));

        //act
        var response = service.startUpload(userId, ContentType.VIDEO,
            new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[] {1, 2, 3}));

        //assert
        assertEquals(contentId, response.contentId());
        assertEquals("processing", response.status());

        //verify
        verify(mediaProcessingService).processUpload(eq(contentId), eq(ContentType.VIDEO), any());
    }

    // Ensures update blocks already submitted content from being modified.
    @Test
    @SuppressWarnings("unchecked")
    void updateDraft_ShouldThrowConflict_WhenContentAlreadySubmitted() {
        //arrange
        ContentDraftService service = new ContentDraftService(adminRestClient, mediaProcessingService);
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content existing = new Content();
        existing.setIsSubmitted(true);
        when(adminRestClient.getList(eq("content"), any(), any(TypeReference.class))).thenReturn(List.of(existing));

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.updateDraft(userId, contentId,
                new ContentUpdateRequest(null, null, null, null, null, null, null, null, null, null)));

        //assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        //verify
        verify(adminRestClient, never()).patchList(eq("content"), any(), any(), any(TypeReference.class));
    }

    // Ensures submit rejects non-ready media status before saving final content payload.
    @Test
    @SuppressWarnings("unchecked")
    void submit_ShouldThrowConflict_WhenMediaIsNotReady() {
        //arrange
        ContentDraftService service = new ContentDraftService(adminRestClient, mediaProcessingService);
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content existing = new Content();
        existing.setIsSubmitted(false);
        ContentMedia media = new ContentMedia();
        media.setStatus("processing");
        when(adminRestClient.getList(eq("content"), any(), any(TypeReference.class))).thenReturn(List.of(existing));
        when(adminRestClient.getList(eq("content_media"), any(), any(TypeReference.class))).thenReturn(List.of(media));

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.submit(userId, contentId,
                new ContentSubmitRequest("t", "d", ContentType.VIDEO, null, null, null, null, null, null, List.of("tag"))));

        //assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        //verify
        verify(adminRestClient, never()).patchList(eq("content"), any(), any(), any(TypeReference.class));
    }

    // Ensures media status endpoint returns transformed response from content_media row.
    @Test
    @SuppressWarnings("unchecked")
    void getMediaStatus_ShouldReturnStatusPayload_WhenContentAndMediaExist() {
        //arrange
        ContentDraftService service = new ContentDraftService(adminRestClient, mediaProcessingService);
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content existing = new Content();
        existing.setIsSubmitted(false);
        ContentMedia media = new ContentMedia();
        media.setStatus("ready");
        media.setHlsUrl("https://hls");
        media.setThumbnailUrl("https://thumb");
        media.setErrorMessage(null);
        when(adminRestClient.getList(eq("content"), any(), any(TypeReference.class))).thenReturn(List.of(existing));
        when(adminRestClient.getList(eq("content_media"), any(), any(TypeReference.class))).thenReturn(List.of(media));

        //act
        var result = service.getMediaStatus(userId, contentId);

        //assert
        assertEquals("ready", result.status());
        assertEquals("https://hls", result.hlsUrl());

        //verify
        verify(adminRestClient).getList(eq("content_media"), any(), any(TypeReference.class));
    }
}

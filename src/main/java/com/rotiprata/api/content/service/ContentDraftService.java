package com.rotiprata.api.content.service;

import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.domain.ContentType;
import com.rotiprata.api.content.dto.ContentMediaStartResponse;
import com.rotiprata.api.content.dto.ContentMediaStatusResponse;
import com.rotiprata.api.content.dto.ContentSubmitRequest;
import com.rotiprata.api.content.dto.ContentUpdateRequest;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Defines the content draft service operations exposed to the API layer.
 */
public interface ContentDraftService {

    /**
     * Starts processing an uploaded media draft.
     */
    ContentMediaStartResponse startUpload(UUID userId, ContentType contentType, MultipartFile file);

    /**
     * Starts processing a linked media draft.
     */
    ContentMediaStartResponse startLink(UUID userId, String sourceUrl);

    /**
     * Updates the draft content metadata.
     */
    Content updateDraft(UUID userId, UUID contentId, ContentUpdateRequest request);

    /**
     * Submits a completed draft for review.
     */
    Content submit(UUID userId, UUID contentId, ContentSubmitRequest request);

    /**
     * Returns the current media processing status.
     */
    ContentMediaStatusResponse getMediaStatus(UUID userId, UUID contentId);
}

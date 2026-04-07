package com.rotiprata.api.content.service;

import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.domain.ContentType;
import com.rotiprata.api.content.dto.ContentMediaStartResponse;
import com.rotiprata.api.content.dto.ContentMediaStatusResponse;
import com.rotiprata.api.content.dto.ContentSubmitRequest;
import com.rotiprata.api.content.dto.ContentUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Service interface for creating, updating, and submitting content drafts.
 * Handles draft creation, media uploads, tag management, and submission.
 */
public interface ContentDraftService {

    /**
     * Starts a media upload for a draft content.
     *
     * @param userId      the ID of the user creating the content
     * @param contentType type of content (cannot be TEXT)
     * @param file        media file to upload
     * @return content media start response containing content ID and poll URL
     * @throws ResponseStatusException if the content type is invalid
     *         or draft creation fails
     */
    ContentMediaStartResponse startUpload(UUID userId, ContentType contentType, MultipartFile file);

    /**
     * Starts a media link submission for a draft content (e.g., YouTube link).
     *
     * @param userId    the ID of the user creating the content
     * @param sourceUrl the URL of the media
     * @return content media start response containing content ID and poll URL
     * @throws ResponseStatusException if the source URL is missing or draft creation fails
     */
    ContentMediaStartResponse startLink(UUID userId, String sourceUrl);

    /**
     * Updates an existing draft content.
     *
     * @param userId    the ID of the user who owns the content
     * @param contentId the ID of the content to update
     * @param request   update request containing new fields and tags
     * @return updated content object
     * @throws ResponseStatusException if the content is not found or already submitted
     */
    Content updateDraft(UUID userId, UUID contentId, ContentUpdateRequest request);

    /**
     * Submits a draft content for review/moderation.
     *
     * @param userId    the ID of the user submitting the content
     * @param contentId the ID of the content to submit
     * @param request   submission request containing tags and optional updates
     * @return submitted content object
     * @throws ResponseStatusException if content/media is not ready, tags are missing, or submission fails
     */
    Content submit(UUID userId, UUID contentId, ContentSubmitRequest request);

    /**
     * Retrieves the current media status of a content.
     *
     * @param userId    the ID of the user requesting the status
     * @param contentId the ID of the content
     * @return media status response containing status, HLS URL, thumbnail, and error message
     * @throws ResponseStatusException if the content or media is not found
     */
    ContentMediaStatusResponse getMediaStatus(UUID userId, UUID contentId);
}
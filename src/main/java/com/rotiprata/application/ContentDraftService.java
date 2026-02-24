package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentMediaStartResponse;
import com.rotiprata.api.dto.ContentMediaStatusResponse;
import com.rotiprata.api.dto.ContentSubmitRequest;
import com.rotiprata.api.dto.ContentUpdateRequest;
import com.rotiprata.domain.Content;
import com.rotiprata.domain.ContentStatus;
import com.rotiprata.domain.ContentTag;
import com.rotiprata.domain.ContentType;
import com.rotiprata.domain.ContentMedia;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentDraftService {
    private static final TypeReference<List<Content>> CONTENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ContentMedia>> MEDIA_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ContentTag>> TAG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int MAX_TITLE = 80;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_OBJECTIVE = 160;
    private static final int MAX_LONG_TEXT = 500;
    private static final int MAX_OLDER_REFERENCE = 160;
    private static final int MAX_TAG = 30;

    private final SupabaseAdminRestClient adminRestClient;
    private final MediaProcessingService mediaProcessingService;

    public ContentDraftService(
        SupabaseAdminRestClient adminRestClient,
        MediaProcessingService mediaProcessingService
    ) {
        this.adminRestClient = adminRestClient;
        this.mediaProcessingService = mediaProcessingService;
    }

    public ContentMediaStartResponse startUpload(UUID userId, ContentType contentType, MultipartFile file) {
        if (contentType == null || contentType == ContentType.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid content type for upload");
        }

        Content content = createDraft(userId, contentType);
        UUID contentId = content.getId();
        createMediaRow(contentId, "upload", null, file.getSize());
        mediaProcessingService.processUpload(contentId, contentType, file);

        return new ContentMediaStartResponse(contentId, "processing", buildPollUrl(contentId));
    }

    public ContentMediaStartResponse startLink(UUID userId, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL is required");
        }

        Content content = createDraft(userId, ContentType.VIDEO);
        UUID contentId = content.getId();
        createMediaRow(contentId, "link", sourceUrl.trim(), null);
        mediaProcessingService.processLink(contentId, sourceUrl.trim());

        return new ContentMediaStartResponse(contentId, "processing", buildPollUrl(contentId));
    }

    public Content updateDraft(UUID userId, UUID contentId, ContentUpdateRequest request) {
        Content existing = requireContent(userId, contentId);
        if (Boolean.TRUE.equals(existing.getIsSubmitted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content already submitted");
        }

        Map<String, Object> patch = new HashMap<>();
        boolean tagsProvided = request.tags() != null;
        if (request.title() != null) {
            patch.put("title", sanitizeRequired(request.title(), MAX_TITLE));
        }
        if (request.description() != null) {
            patch.put("description", sanitizeRequired(request.description(), MAX_DESCRIPTION));
        }
        if (request.contentType() != null) {
            patch.put("content_type", request.contentType().toJson());
        }
        if (request.categoryId() != null) {
            patch.put("category_id", request.categoryId());
        }
        if (request.learningObjective() != null) {
            patch.put("learning_objective", sanitizeOptional(request.learningObjective(), MAX_OBJECTIVE));
        }
        if (request.originExplanation() != null) {
            patch.put("origin_explanation", sanitizeOptional(request.originExplanation(), MAX_LONG_TEXT));
        }
        if (request.definitionLiteral() != null) {
            patch.put("definition_literal", sanitizeOptional(request.definitionLiteral(), MAX_LONG_TEXT));
        }
        if (request.definitionUsed() != null) {
            patch.put("definition_used", sanitizeOptional(request.definitionUsed(), MAX_LONG_TEXT));
        }
        if (request.olderVersionReference() != null) {
            patch.put("older_version_reference", sanitizeOptional(request.olderVersionReference(), MAX_OLDER_REFERENCE));
        }

        if (!patch.isEmpty()) {
            patch.put("updated_at", OffsetDateTime.now());
            List<Content> updated = adminRestClient.patchList(
                "content",
                buildQuery(Map.of("id", "eq." + contentId)),
                patch,
                CONTENT_LIST
            );
            if (!updated.isEmpty()) {
                existing = updated.get(0);
            }
        }

        if (tagsProvided) {
            replaceTags(contentId, request.tags());
        }

        return existing;
    }

    public Content submit(UUID userId, UUID contentId, ContentSubmitRequest request) {
        Content existing = requireContent(userId, contentId);
        if (Boolean.TRUE.equals(existing.getIsSubmitted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Content already submitted");
        }

        ContentMedia media = requireMedia(contentId);
        String mediaStatus = media.getStatus();
        if (mediaStatus == null || !"ready".equalsIgnoreCase(mediaStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media processing is not complete");
        }

        Map<String, Object> patch = new HashMap<>();
        patch.put("title", sanitizeRequired(request.title(), MAX_TITLE));
        patch.put("description", sanitizeRequired(request.description(), MAX_DESCRIPTION));
        patch.put("content_type", request.contentType().toJson());
        patch.put("learning_objective", sanitizeOptional(request.learningObjective(), MAX_OBJECTIVE));
        patch.put("origin_explanation", sanitizeOptional(request.originExplanation(), MAX_LONG_TEXT));
        patch.put("definition_literal", sanitizeOptional(request.definitionLiteral(), MAX_LONG_TEXT));
        patch.put("definition_used", sanitizeOptional(request.definitionUsed(), MAX_LONG_TEXT));
        patch.put("older_version_reference", sanitizeOptional(request.olderVersionReference(), MAX_OLDER_REFERENCE));
        patch.put("status", ContentStatus.PENDING.toJson());
        patch.put("is_submitted", true);
        patch.put("updated_at", OffsetDateTime.now());

        if (request.categoryId() != null) {
            patch.put("category_id", request.categoryId());
        }

        List<Content> updated = adminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            patch,
            CONTENT_LIST
        );
        Content result = updated.isEmpty() ? existing : updated.get(0);

        ensureModerationQueueEntry(contentId);

        replaceTags(contentId, request.tags());
        return result;
    }

    public ContentMediaStatusResponse getMediaStatus(UUID userId, UUID contentId) {
        requireContent(userId, contentId);
        ContentMedia media = requireMedia(contentId);
        return new ContentMediaStatusResponse(
            media.getStatus(),
            media.getHlsUrl(),
            media.getThumbnailUrl(),
            media.getErrorMessage()
        );
    }

    private Content createDraft(UUID userId, ContentType contentType) {
        Map<String, Object> body = new HashMap<>();
        body.put("creator_id", userId);
        body.put("content_type", contentType.toJson());
        body.put("title", "Draft content");
        body.put("description", "");
        body.put("status", ContentStatus.PENDING.toJson());
        body.put("is_submitted", false);
        body.put("updated_at", OffsetDateTime.now());
        body.put("created_at", OffsetDateTime.now());

        List<Content> created = adminRestClient.postList("content", body, CONTENT_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create draft content");
        }
        return created.get(0);
    }

    private void createMediaRow(UUID contentId, String sourceType, String sourceUrl, Long sizeBytes) {
        Map<String, Object> body = new HashMap<>();
        body.put("content_id", contentId);
        body.put("source_type", sourceType);
        body.put("status", "processing");
        if (sourceUrl != null) {
            body.put("source_url", sourceUrl);
        }
        if (sizeBytes != null) {
            body.put("size_bytes", sizeBytes);
        }
        body.put("updated_at", OffsetDateTime.now());
        body.put("created_at", OffsetDateTime.now());
        adminRestClient.postList("content_media", body, MEDIA_LIST);
    }

    private Content requireContent(UUID userId, UUID contentId) {
        List<Content> contents = adminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "id", "eq." + contentId,
                "creator_id", "eq." + userId
            )),
            CONTENT_LIST
        );
        if (contents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
        return contents.get(0);
    }

    private ContentMedia requireMedia(UUID contentId) {
        List<ContentMedia> media = adminRestClient.getList(
            "content_media",
            buildQuery(Map.of("content_id", "eq." + contentId)),
            MEDIA_LIST
        );
        if (media.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
        }
        return media.get(0);
    }

    private void replaceTags(UUID contentId, List<String> tags) {
        if (tags == null) {
            return;
        }
        try {
            adminRestClient.deleteList(
                "content_tags",
                buildQuery(Map.of("content_id", "eq." + contentId)),
                TAG_LIST
            );
        } catch (ResponseStatusException ex) {
            // Tags are optional; don't block submission.
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String sanitized = sanitizeTag(tag);
            if (sanitized != null && !sanitized.isBlank()) {
                normalized.add(sanitized);
            }
        }
        if (normalized.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = normalized.stream()
            .map(tag -> {
                Map<String, Object> row = new HashMap<>();
                row.put("content_id", contentId);
                row.put("tag", tag);
                return row;
            })
            .toList();
        try {
            adminRestClient.postList("content_tags", rows, TAG_LIST);
        } catch (ResponseStatusException ex) {
            // Tags are optional; log in server logs if needed, but don't block submission.
        }
    }

    private void ensureModerationQueueEntry(UUID contentId) {
        try {
            List<Map<String, Object>> existing = adminRestClient.getList(
                "moderation_queue",
                buildQuery(Map.of("content_id", "eq." + contentId, "select", "id")),
                MAP_LIST
            );
            if (!existing.isEmpty()) {
                return;
            }
            adminRestClient.postList(
                "moderation_queue",
                Map.of(
                    "content_id", contentId,
                    "submitted_at", OffsetDateTime.now()
                ),
                MAP_LIST
            );
        } catch (ResponseStatusException ex) {
            // If another request inserted first, unique constraint can race.
            if (ex.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw ex;
            }
        }
    }

    private String buildPollUrl(UUID contentId) {
        return "/api/content/" + contentId + "/media";
    }

    private String sanitizeRequired(String value, int maxLength) {
        String sanitized = sanitizeText(value, maxLength);
        return sanitized == null ? "" : sanitized;
    }

    private String sanitizeOptional(String value, int maxLength) {
        String sanitized = sanitizeText(value, maxLength);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }
        return sanitized;
    }

    private String sanitizeTag(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return sanitizeText(trimmed, MAX_TAG);
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\x00-\\x1F\\x7F]", "");
        String collapsed = cleaned.replaceAll("\\s+", " ").trim();
        if (maxLength > 0 && collapsed.length() > maxLength) {
            return collapsed.substring(0, maxLength);
        }
        return collapsed;
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }
}

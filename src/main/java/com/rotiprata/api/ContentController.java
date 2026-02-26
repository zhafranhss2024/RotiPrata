package com.rotiprata.api;

import com.rotiprata.api.dto.ContentMediaStartLinkRequest;
import com.rotiprata.api.dto.ContentMediaStartResponse;
import com.rotiprata.api.dto.ContentMediaStatusResponse;
import com.rotiprata.api.dto.ContentSubmitRequest;
import com.rotiprata.api.dto.ContentUpdateRequest;
import com.rotiprata.application.ContentService;
import com.rotiprata.application.ContentDraftService;
import com.rotiprata.domain.Content;
import com.rotiprata.domain.ContentType;
import com.rotiprata.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/content")
public class ContentController {
    private final ContentDraftService contentDraftService;
    private final ContentService contentService;

    public ContentController(ContentDraftService contentDraftService, ContentService contentService) {
        this.contentDraftService = contentDraftService;
        this.contentService = contentService;
    }

    @PostMapping("/media/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContentMediaStartResponse startUpload(
        @AuthenticationPrincipal Jwt jwt,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File is required"
            );
        }
        ContentType contentType = detectContentType(file);
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.startUpload(userId, contentType, file);
    }

    @PostMapping("/media/start-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContentMediaStartResponse startLink(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ContentMediaStartLinkRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.startLink(userId, request.sourceUrl());
    }

    @PatchMapping("/{contentId}")
    public Content updateDraft(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody ContentUpdateRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.updateDraft(userId, contentId, request);
    }

    @PostMapping("/{contentId}/submit")
    public Content submit(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentSubmitRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.submit(userId, contentId, request);
    }

    @GetMapping("/{contentId}/media")
    public ContentMediaStatusResponse mediaStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.getMediaStatus(userId, contentId);
    }

    @PostMapping("/{contentId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trackView(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.trackView(userId, contentId);
    }

    private ContentType detectContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Missing content type"
            );
        }
        if (contentType.startsWith("video/")) {
            return ContentType.VIDEO;
        }
        if (contentType.startsWith("image/")) {
            return ContentType.IMAGE;
        }
        throw new org.springframework.web.server.ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Only video or image uploads are supported"
        );
    }
}

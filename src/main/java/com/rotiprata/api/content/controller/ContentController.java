package com.rotiprata.api.content.controller;

import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.domain.ContentType;
import com.rotiprata.api.content.dto.ContentCommentCreateRequest;
import com.rotiprata.api.content.dto.ContentCommentResponse;
import com.rotiprata.api.content.dto.ContentFlagRequest;
import com.rotiprata.api.content.dto.ContentMediaStartLinkRequest;
import com.rotiprata.api.content.dto.ContentMediaStartResponse;
import com.rotiprata.api.content.dto.ContentMediaStatusResponse;
import com.rotiprata.api.content.dto.ContentPlaybackEventRequest;
import com.rotiprata.api.content.dto.ContentQuizResponse;
import com.rotiprata.api.content.dto.ContentQuizSubmitRequest;
import com.rotiprata.api.content.dto.ContentQuizSubmitResponse;
import com.rotiprata.api.content.dto.ContentSubmitRequest;
import com.rotiprata.api.content.dto.ContentUpdateRequest;
import com.rotiprata.api.content.service.ContentDraftService;
import com.rotiprata.api.content.service.ContentQuizService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Exposes REST endpoints for the content controller flows.
 */
@RestController
@RequestMapping("/api/content")
public class ContentController {
    private final ContentDraftService contentDraftService;
    private final ContentService contentService;
    private final ContentQuizService contentQuizService;

    /**
     * Creates a content controller instance with its collaborators.
     */
    public ContentController(
        ContentDraftService contentDraftService,
        ContentService contentService,
        ContentQuizService contentQuizService
    ) {
        this.contentDraftService = contentDraftService;
        this.contentService = contentService;
        this.contentQuizService = contentQuizService;
    }

    /**
     * Starts the upload.
     */
    @PostMapping("/uploads")
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

    /**
     * Starts the upload alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/media/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContentMediaStartResponse startUploadAlias(
        @AuthenticationPrincipal Jwt jwt,
        @RequestPart("file") MultipartFile file
    ) {
        return startUpload(jwt, file);
    }

    /**
     * Starts the link.
     */
    @PostMapping("/link-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContentMediaStartResponse startLink(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ContentMediaStartLinkRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.startLink(userId, request.sourceUrl());
    }

    /**
     * Starts the link alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/media/start-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContentMediaStartResponse startLinkAlias(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ContentMediaStartLinkRequest request
    ) {
        return startLink(jwt, request);
    }

    /**
     * Updates the draft.
     */
    @PatchMapping("/{contentId}")
    public Content updateDraft(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody ContentUpdateRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.updateDraft(userId, contentId, request);
    }

    /**
     * Creates the submission.
     */
    @PostMapping("/{contentId}/submission")
    public Content createSubmission(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentSubmitRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.submit(userId, contentId, request);
    }

    /**
     * Handles submit.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/submit")
    public Content submit(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentSubmitRequest request
    ) {
        return createSubmission(jwt, contentId, request);
    }

    /**
     * Handles media status.
     */
    @GetMapping("/{contentId}/media")
    public ContentMediaStatusResponse mediaStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentDraftService.getMediaStatus(userId, contentId);
    }

    /**
     * Returns the content.
     */
    @GetMapping("/{contentId}")
    public Map<String, Object> getContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.getContentById(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Returns the similar content.
     */
    @GetMapping("/{contentId}/similar")
    public List<Map<String, Object>> getSimilarContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.getSimilarContent(userId, contentId, SecurityUtils.getAccessToken(), limit);
    }

    /**
     * Tracks the view.
     */
    @PostMapping("/{contentId}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trackView(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.trackView(userId, contentId);
    }

    /**
     * Tracks the view alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trackViewAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        trackView(jwt, contentId);
    }

    /**
     * Tracks the playback event.
     */
    @PostMapping("/{contentId}/playback-events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void trackPlaybackEvent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentPlaybackEventRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.recordPlaybackEvent(userId, contentId, request);
    }

    /**
     * Handles like.
     */
    @PostMapping("/{contentId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.likeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Likes the alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        like(jwt, contentId);
    }

    /**
     * Handles unlike.
     */
    @DeleteMapping("/{contentId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.unlikeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Removes the like from the alias.
     */
    @Hidden
    @Deprecated
    @DeleteMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        unlike(jwt, contentId);
    }

    /**
     * Handles save.
     */
    @PostMapping("/{contentId}/saves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.saveContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Saves the alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        save(jwt, contentId);
    }

    /**
     * Handles unsave.
     */
    @DeleteMapping("/{contentId}/saves")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.unsaveContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Removes the saved marker from the alias.
     */
    @Hidden
    @Deprecated
    @DeleteMapping("/{contentId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsaveAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        unsave(jwt, contentId);
    }

    /**
     * Handles share.
     */
    @PostMapping("/{contentId}/shares")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.shareContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    /**
     * Records a share for the alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void shareAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        share(jwt, contentId);
    }

    /**
     * Handles content quiz.
     */
    @GetMapping("/{contentId}/quiz")
    public ResponseEntity<ContentQuizResponse> contentQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        ContentQuizResponse quiz = contentQuizService.getContentQuiz(userId, contentId, SecurityUtils.getAccessToken());
        if (quiz == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(quiz);
    }

    /**
     * Creates the content quiz submission.
     */
    @PostMapping("/{contentId}/quiz-submissions")
    public ContentQuizSubmitResponse createContentQuizSubmission(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody ContentQuizSubmitRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentQuizService.submitContentQuiz(userId, contentId, request, SecurityUtils.getAccessToken());
    }

    /**
     * Submits the content quiz.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/quiz/submit")
    public ContentQuizSubmitResponse submitContentQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody ContentQuizSubmitRequest request
    ) {
        return createContentQuizSubmission(jwt, contentId, request);
    }

    /**
     * Handles comments.
     */
    @GetMapping("/{contentId}/comments")
    public List<ContentCommentResponse> comments(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.listComments(userId, contentId, limit, offset, SecurityUtils.getAccessToken());
    }

    /**
     * Creates the comment.
     */
    @PostMapping("/{contentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentCommentResponse createComment(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentCommentCreateRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.createComment(userId, contentId, request, SecurityUtils.getAccessToken());
    }

    /**
     * Deletes the comment.
     */
    @DeleteMapping("/{contentId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @PathVariable UUID commentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.deleteComment(userId, contentId, commentId, SecurityUtils.getAccessToken());
    }

    /**
     * Handles flag.
     */
    @PostMapping("/{contentId}/flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void flag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentFlagRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.flagContent(userId, contentId, request, SecurityUtils.getAccessToken());
    }

    /**
     * Flags the alias.
     */
    @Hidden
    @Deprecated
    @PostMapping("/{contentId}/flag")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void flagAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentFlagRequest request
    ) {
        flag(jwt, contentId, request);
    }

    /**
     * Detects the content type.
     */
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

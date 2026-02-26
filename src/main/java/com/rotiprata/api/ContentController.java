package com.rotiprata.api;

import com.rotiprata.api.dto.ContentMediaStartLinkRequest;
import com.rotiprata.api.dto.ContentMediaStartResponse;
import com.rotiprata.api.dto.ContentMediaStatusResponse;
import com.rotiprata.api.dto.ContentVoteRequest;
import com.rotiprata.api.dto.ContentFlagRequest;
import com.rotiprata.api.dto.ContentCommentCreateRequest;
import com.rotiprata.api.dto.ContentCommentResponse;
import com.rotiprata.api.dto.ContentSubmitRequest;
import com.rotiprata.api.dto.ContentUpdateRequest;
import com.rotiprata.api.dto.ContentQuizResponse;
import com.rotiprata.api.dto.ContentQuizSubmitRequest;
import com.rotiprata.api.dto.ContentQuizSubmitResponse;
import com.rotiprata.application.ContentService;
import com.rotiprata.application.ContentDraftService;
import com.rotiprata.application.ContentQuizService;
import com.rotiprata.domain.Content;
import com.rotiprata.domain.ContentType;
import com.rotiprata.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ContentQuizService contentQuizService;

    public ContentController(
        ContentDraftService contentDraftService,
        ContentService contentService,
        ContentQuizService contentQuizService
    ) {
        this.contentDraftService = contentDraftService;
        this.contentService = contentService;
        this.contentQuizService = contentQuizService;
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

    @GetMapping("/{contentId}")
    public Map<String, Object> getContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.getContentById(userId, contentId, SecurityUtils.getAccessToken());
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

    @PostMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.likeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    @DeleteMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.unlikeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    // Deprecated alias kept for backward compatibility with older frontend calls.
    @PostMapping("/{contentId}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody(required = false) ContentVoteRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        String voteType = request != null ? request.voteType() : null;
        if (voteType != null && !voteType.isBlank() && !"educational".equalsIgnoreCase(voteType)) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported vote type"
            );
        }
        contentService.likeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    // Deprecated alias kept for backward compatibility with older frontend calls.
    @DeleteMapping("/{contentId}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unvote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.unlikeContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/{contentId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.saveContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    @DeleteMapping("/{contentId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.unsaveContent(userId, contentId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/{contentId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.shareContent(userId, contentId, SecurityUtils.getAccessToken());
    }

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

    @PostMapping("/{contentId}/quiz/submit")
    public ContentQuizSubmitResponse submitContentQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestBody ContentQuizSubmitRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentQuizService.submitContentQuiz(userId, contentId, request, SecurityUtils.getAccessToken());
    }

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

    @PostMapping("/{contentId}/flag")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void flag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody ContentFlagRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        contentService.flagContent(userId, contentId, request, SecurityUtils.getAccessToken());
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

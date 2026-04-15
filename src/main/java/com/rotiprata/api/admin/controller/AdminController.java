package com.rotiprata.api.admin.controller;

import com.rotiprata.api.admin.dto.AdminContentQuizRequest;
import com.rotiprata.api.admin.dto.AdminContentReviewRequest;
import com.rotiprata.api.admin.dto.AdminContentUpdateRequest;
import com.rotiprata.api.admin.dto.AdminFlagResolutionRequest;
import com.rotiprata.api.admin.dto.AdminStatsResponse;
import com.rotiprata.api.admin.dto.AdminUserDetailResponse;
import com.rotiprata.api.admin.dto.AdminUserRoleUpdateRequest;
import com.rotiprata.api.admin.dto.AdminUserStatusUpdateRequest;
import com.rotiprata.api.admin.dto.AdminUserSummaryResponse;
import com.rotiprata.api.admin.service.AdminService;
import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.dto.ContentQuizQuestionResponse;
import com.rotiprata.api.content.service.ContentQuizService;
import com.rotiprata.api.admin.request.RejectContentRequest;
import com.rotiprata.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final ContentQuizService contentQuizService;

    public AdminController(AdminService adminService, ContentQuizService contentQuizService) {
        this.adminService = adminService;
        this.contentQuizService = contentQuizService;
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getStats(adminUserId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/users")
    public List<AdminUserSummaryResponse> users(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String query
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getUsers(adminUserId, query, SecurityUtils.getAccessToken());
    }

    @GetMapping("/users/{userId}")
    public AdminUserDetailResponse userDetail(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID userId
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getUserDetail(adminUserId, userId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/users/{userId}/role")
    public AdminUserSummaryResponse updateUserRole(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserRoleUpdateRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.updateUserRole(adminUserId, userId, request.role(), SecurityUtils.getAccessToken());
    }

    @PutMapping("/users/{userId}/status")
    public AdminUserSummaryResponse updateUserStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserStatusUpdateRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.updateUserStatus(adminUserId, userId, request.status(), SecurityUtils.getAccessToken());
    }

    @DeleteMapping("/users/{userId}/lessons/{lessonId}/progress")
    public ResponseEntity<Void> resetLessonProgress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID userId,
        @PathVariable UUID lessonId
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        adminService.resetUserLessonProgress(adminUserId, userId, lessonId, SecurityUtils.getAccessToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderation-queue")
    public List<Map<String, Object>> moderationQueue(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getModerationQueue(adminUserId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/content/{contentId}/review")
    public ResponseEntity<Void> reviewContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody AdminContentReviewRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        switch (normalizeActionStatus(request.status())) {
            case "approved" -> adminService.approveContent(adminUserId, contentId, SecurityUtils.getAccessToken());
            case "rejected" -> adminService.rejectContent(adminUserId, contentId, request.feedback(), SecurityUtils.getAccessToken());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported review status");
        }
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @Deprecated
    @PutMapping("/content/{contentId}/approve")
    public ResponseEntity<Void> approveContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        return reviewContent(jwt, contentId, new AdminContentReviewRequest("approved", null));
    }

    @PutMapping("/content/{contentId}")
    public Content updateContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody AdminContentUpdateRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.updateContentMetadata(adminUserId, contentId, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PutMapping("/content/{contentId}/reject")
    public ResponseEntity<Void> rejectContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody RejectContentRequest request
    ) {
        return reviewContent(jwt, contentId, new AdminContentReviewRequest("rejected", request.feedback()));
    }

    @GetMapping("/content/{contentId}/quiz")
    public List<ContentQuizQuestionResponse> contentQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return contentQuizService.getAdminContentQuiz(adminUserId, contentId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/content/{contentId}/quiz")
    public List<ContentQuizQuestionResponse> updateContentQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody AdminContentQuizRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return contentQuizService.replaceAdminContentQuiz(adminUserId, contentId, request, SecurityUtils.getAccessToken());
    }

    @GetMapping("/flags")
    public List<Map<String, Object>> flags(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getOpenFlags(adminUserId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/flags/{flagId}/reports")
    public Map<String, Object> flagReports(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID flagId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String query
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getFlagReports(adminUserId, flagId, page, query, SecurityUtils.getAccessToken());
    }

    @GetMapping("/flags/content/{contentId}/review")
    public Map<String, Object> flagReviewByContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getFlagReviewByContent(
            adminUserId,
            contentId,
            month,
            year,
            SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/flags/content/{contentId}/reports")
    public Map<String, Object> flagReportsByContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer month,
        @RequestParam(required = false) Integer year
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getFlagReportsByContent(
            adminUserId,
            contentId,
            page,
            query,
            month,
            year,
            SecurityUtils.getAccessToken()
        );
    }

    @PutMapping("/flags/{flagId}/resolution")
    public ResponseEntity<Void> resolveFlag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID flagId,
        @Valid @RequestBody AdminFlagResolutionRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        switch (normalizeActionStatus(request.status())) {
            case "resolved" -> adminService.resolveFlag(adminUserId, flagId, SecurityUtils.getAccessToken());
            case "taken_down" -> adminService.takeDownFlag(adminUserId, flagId, request.feedback(), SecurityUtils.getAccessToken());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resolution status");
        }
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @Deprecated
    @PutMapping("/flags/{flagId}/resolve")
    public ResponseEntity<Void> resolveFlagAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID flagId
    ) {
        return resolveFlag(jwt, flagId, new AdminFlagResolutionRequest("resolved", null));
    }

    @Hidden
    @Deprecated
    @PutMapping("/flags/{flagId}/take-down")
    public ResponseEntity<Void> takeDownFlag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID flagId,
        @Valid @RequestBody RejectContentRequest request
    ) {
        return resolveFlag(jwt, flagId, new AdminFlagResolutionRequest("taken_down", request.feedback()));
    }

    private String normalizeActionStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }
}

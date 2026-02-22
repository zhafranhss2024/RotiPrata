package com.rotiprata.api;

import com.rotiprata.api.dto.AdminStatsResponse;
import com.rotiprata.api.dto.AdminContentUpdateRequest;
import com.rotiprata.api.dto.RejectContentRequest;
import com.rotiprata.application.AdminService;
import com.rotiprata.domain.Content;
import com.rotiprata.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getStats(adminUserId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/moderation-queue")
    public List<Map<String, Object>> moderationQueue(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getModerationQueue(adminUserId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/content/{contentId}/approve")
    public ResponseEntity<Void> approveContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        adminService.approveContent(adminUserId, contentId, SecurityUtils.getAccessToken());
        return ResponseEntity.noContent().build();
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

    @PutMapping("/content/{contentId}/reject")
    public ResponseEntity<Void> rejectContent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID contentId,
        @Valid @RequestBody RejectContentRequest request
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        adminService.rejectContent(adminUserId, contentId, request.feedback(), SecurityUtils.getAccessToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/flags")
    public List<Map<String, Object>> flags(@AuthenticationPrincipal Jwt jwt) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        return adminService.getOpenFlags(adminUserId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/flags/{flagId}/resolve")
    public ResponseEntity<Void> resolveFlag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID flagId
    ) {
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        adminService.resolveFlag(adminUserId, flagId, SecurityUtils.getAccessToken());
        return ResponseEntity.noContent().build();
    }
}

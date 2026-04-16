package com.rotiprata.api.admin.service;

import com.rotiprata.api.admin.dto.AdminContentUpdateRequest;
import com.rotiprata.api.admin.dto.AdminStatsResponse;
import com.rotiprata.api.admin.dto.AdminUserDetailResponse;
import com.rotiprata.api.admin.dto.AdminUserSummaryResponse;
import com.rotiprata.api.content.domain.Content;
import com.rotiprata.security.authorization.AppRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defines the admin service operations exposed to the API layer.
 */
public interface AdminService {

    /**
     * Returns the moderation queue.
     */
    List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken);

    /**
     * Handles approve content.
     */
    void approveContent(UUID adminUserId, UUID contentId, String accessToken);

    /**
     * Handles reject content.
     */
    void rejectContent(UUID adminUserId, UUID contentId, String feedback, String accessToken);

    /**
     * Updates the content metadata.
     */
    Content updateContentMetadata(
        UUID adminUserId,
        UUID contentId,
        AdminContentUpdateRequest request,
        String accessToken
    );

    /**
     * Returns the open flags.
     */
    List<Map<String, Object>> getOpenFlags(UUID adminUserId, String accessToken);

    /**
     * Resolves the flag.
     */
    void resolveFlag(UUID adminUserId, UUID flagId, String accessToken);

    /**
     * Handles take down flag.
     */
    void takeDownFlag(UUID adminUserId, UUID flagId, String feedback, String accessToken);

    /**
     * Returns the flag reports.
     */
    Map<String, Object> getFlagReports(
        UUID adminUserId,
        UUID flagId,
        int page,
        String query,
        String accessToken
    );

    /**
     * Returns the flag review by content.
     */
    Map<String, Object> getFlagReviewByContent(
        UUID adminUserId,
        UUID contentId,
        Integer month,
        Integer year,
        String accessToken
    );

    /**
     * Returns the flag reports by content.
     */
    Map<String, Object> getFlagReportsByContent(
        UUID adminUserId,
        UUID contentId,
        int page,
        String query,
        Integer month,
        Integer year,
        String accessToken
    );

    /**
     * Returns the stats.
     */
    AdminStatsResponse getStats(UUID adminUserId, String accessToken);

    /**
     * Returns the users.
     */
    List<AdminUserSummaryResponse> getUsers(UUID adminUserId, String searchQuery, String accessToken);

    /**
     * Returns the user detail.
     */
    AdminUserDetailResponse getUserDetail(UUID adminUserId, UUID targetUserId, String accessToken);

    /**
     * Updates the user role.
     */
    AdminUserSummaryResponse updateUserRole(
        UUID adminUserId,
        UUID targetUserId,
        AppRole role,
        String accessToken
    );

    /**
     * Updates the user status.
     */
    AdminUserSummaryResponse updateUserStatus(
        UUID adminUserId,
        UUID targetUserId,
        String status,
        String accessToken
    );

    /**
     * Handles reset user lesson progress.
     */
    void resetUserLessonProgress(
        UUID adminUserId,
        UUID targetUserId,
        UUID lessonId,
        String accessToken
    );
}

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

public interface AdminService {

    List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken);

    void approveContent(UUID adminUserId, UUID contentId, String accessToken);

    void rejectContent(UUID adminUserId, UUID contentId, String feedback, String accessToken);

    Content updateContentMetadata(
        UUID adminUserId,
        UUID contentId,
        AdminContentUpdateRequest request,
        String accessToken
    );

    List<Map<String, Object>> getOpenFlags(UUID adminUserId, String accessToken);

    void resolveFlag(UUID adminUserId, UUID flagId, String accessToken);

    void takeDownFlag(UUID adminUserId, UUID flagId, String feedback, String accessToken);

    Map<String, Object> getFlagReports(
        UUID adminUserId,
        UUID flagId,
        int page,
        String query,
        String accessToken
    );

    Map<String, Object> getFlagReviewByContent(
        UUID adminUserId,
        UUID contentId,
        Integer month,
        Integer year,
        String accessToken
    );

    Map<String, Object> getFlagReportsByContent(
        UUID adminUserId,
        UUID contentId,
        int page,
        String query,
        Integer month,
        Integer year,
        String accessToken
    );

    AdminStatsResponse getStats(UUID adminUserId, String accessToken);

    List<AdminUserSummaryResponse> getUsers(UUID adminUserId, String searchQuery, String accessToken);

    AdminUserDetailResponse getUserDetail(UUID adminUserId, UUID targetUserId, String accessToken);

    AdminUserSummaryResponse updateUserRole(
        UUID adminUserId,
        UUID targetUserId,
        AppRole role,
        String accessToken
    );

    AdminUserSummaryResponse updateUserStatus(
        UUID adminUserId,
        UUID targetUserId,
        String status,
        String accessToken
    );

    void resetUserLessonProgress(
        UUID adminUserId,
        UUID targetUserId,
        UUID lessonId,
        String accessToken
    );
}

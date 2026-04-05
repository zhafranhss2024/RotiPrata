package com.rotiprata.api.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.user.dto.UserBadgeResponse;

public record AdminUserDetailResponse(
    AdminUserSummaryResponse summary,
    OffsetDateTime suspendedUntil,
    AdminUserActivityStatsResponse activity,
    List<Map<String, Object>> postedContent,
    List<Map<String, Object>> likedContent,
    List<Map<String, Object>> savedContent,
    List<AdminUserCommentResponse> comments,
    List<AdminUserLessonProgressResponse> lessonProgress,
    List<UserBadgeResponse> badges,
    List<AdminUserBrowsingHistoryResponse> browsingHistory,
    List<AdminUserSearchHistoryResponse> searchHistory,
    List<ChatbotMessageDTO> chatHistory
) {}

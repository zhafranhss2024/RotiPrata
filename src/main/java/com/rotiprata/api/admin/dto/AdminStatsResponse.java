package com.rotiprata.api.admin.dto;

public record AdminStatsResponse(
    int totalUsers,
    int activeUsers,
    int totalContent,
    int pendingModeration,
    int totalLessons,
    int contentApprovalRate
) {}

package com.rotiprata.api.dto;

public record AdminStatsResponse(
    int totalUsers,
    int activeUsers,
    int totalContent,
    int pendingModeration,
    int totalLessons,
    int contentApprovalRate
) {}

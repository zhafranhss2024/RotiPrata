package com.rotiprata.api.dto;

public record AdminUserActivityStatsResponse(
    int postedContentCount,
    int likedContentCount,
    int savedContentCount,
    int commentCount,
    int enrolledLessonCount,
    int completedLessonCount,
    int badgeCount,
    int browsingCount,
    int searchCount,
    int chatMessageCount
) {}

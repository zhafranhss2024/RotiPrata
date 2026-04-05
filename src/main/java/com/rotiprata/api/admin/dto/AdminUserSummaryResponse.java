package com.rotiprata.api.admin.dto;

import com.rotiprata.domain.AppRole;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserSummaryResponse(
    UUID userId,
    String displayName,
    String email,
    String avatarUrl,
    int reputationPoints,
    int currentStreak,
    int longestStreak,
    LocalDate lastActivityDate,
    double totalHoursLearned,
    List<AppRole> roles,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime lastSignInAt
) {}

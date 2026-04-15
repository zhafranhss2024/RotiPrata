package com.rotiprata.api.user.response;

import java.util.UUID;

/**
 * Represents the leaderboard entry response payload returned by the API layer.
 */
public record LeaderboardEntryResponse(
    int rank,
    UUID userId,
    String displayName,
    String avatarUrl,
    int xp,
    int currentStreak,
    boolean isCurrentUser
) {}

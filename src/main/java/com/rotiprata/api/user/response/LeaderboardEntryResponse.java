package com.rotiprata.api.zdto;

import java.util.UUID;

public record LeaderboardEntryResponse(
    int rank,
    UUID userId,
    String displayName,
    String avatarUrl,
    int xp,
    int currentStreak,
    boolean isCurrentUser
) {}

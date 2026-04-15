package com.rotiprata.api.user.response;

import java.util.List;

public record LeaderboardResponse(
    List<LeaderboardEntryResponse> items,
    int page,
    int pageSize,
    boolean hasNext,
    int totalCount,
    String query,
    LeaderboardEntryResponse currentUser
) {}

package com.rotiprata.api.dto;

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

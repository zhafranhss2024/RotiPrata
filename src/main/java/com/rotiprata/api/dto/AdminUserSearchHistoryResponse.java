package com.rotiprata.api.dto;

import java.time.LocalDateTime;

public record AdminUserSearchHistoryResponse(
    String id,
    String query,
    LocalDateTime searchedAt
) {}

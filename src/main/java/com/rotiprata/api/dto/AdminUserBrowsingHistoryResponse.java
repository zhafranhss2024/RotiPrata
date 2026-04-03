package com.rotiprata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserBrowsingHistoryResponse(
    UUID id,
    UUID contentId,
    UUID lessonId,
    UUID itemId,
    String title,
    OffsetDateTime viewedAt
) {}

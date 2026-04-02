package com.rotiprata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserCommentResponse(
    UUID id,
    UUID contentId,
    String contentTitle,
    String body,
    String author,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

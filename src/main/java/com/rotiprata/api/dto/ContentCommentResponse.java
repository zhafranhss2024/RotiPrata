package com.rotiprata.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContentCommentResponse(
    UUID id,
    UUID contentId,
    UUID userId,
    UUID parentId,
    String body,
    String author,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

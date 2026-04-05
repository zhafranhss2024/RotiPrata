package com.rotiprata.api.lesson.dto;

import java.util.UUID;

public record LessonMediaStatusResponse(
    UUID assetId,
    String status,
    String mediaKind,
    String playbackUrl,
    String thumbnailUrl,
    String errorMessage
) {}

package com.rotiprata.api.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserBadgeResponse(
    UUID lessonId,
    String lessonTitle,
    String badgeName,
    String badgeIconUrl,
    boolean earned,
    OffsetDateTime earnedAt
) {}

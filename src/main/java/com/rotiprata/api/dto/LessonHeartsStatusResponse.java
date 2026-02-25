package com.rotiprata.api.dto;

import java.time.OffsetDateTime;

public record LessonHeartsStatusResponse(
    int heartsRemaining,
    OffsetDateTime heartsRefillAt
) {}

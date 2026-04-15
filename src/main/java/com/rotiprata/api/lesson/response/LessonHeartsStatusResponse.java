package com.rotiprata.api.lesson.dto;

import java.time.OffsetDateTime;

public record LessonHeartsStatusResponse(
    int heartsRemaining,
    OffsetDateTime heartsRefillAt
) {}

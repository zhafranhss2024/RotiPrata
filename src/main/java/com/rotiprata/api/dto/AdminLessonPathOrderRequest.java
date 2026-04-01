package com.rotiprata.api.dto;

import java.util.List;
import java.util.UUID;

public record AdminLessonPathOrderRequest(
    UUID categoryId,
    List<UUID> lessonIds
) {}

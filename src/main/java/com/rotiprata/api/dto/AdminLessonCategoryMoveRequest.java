package com.rotiprata.api.dto;

import java.util.List;
import java.util.UUID;

public record AdminLessonCategoryMoveRequest(
    UUID sourceCategoryId,
    UUID targetCategoryId,
    List<UUID> sourceLessonIds,
    List<UUID> targetLessonIds
) {}

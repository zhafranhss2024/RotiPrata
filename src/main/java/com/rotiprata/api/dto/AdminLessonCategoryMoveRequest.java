package com.rotiprata.api.dto;

import java.util.UUID;

public record AdminLessonCategoryMoveRequest(
    UUID sourceCategoryId,
    UUID targetCategoryId
) {}

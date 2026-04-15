package com.rotiprata.api.admin.dto;

import java.util.UUID;

public record AdminLessonCategoryMoveRequest(
    UUID sourceCategoryId,
    UUID targetCategoryId
) {}

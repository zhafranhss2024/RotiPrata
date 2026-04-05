package com.rotiprata.api.lesson.dto;

import java.util.UUID;

public record LessonMediaStartResponse(UUID assetId, String status, String pollUrl) {}

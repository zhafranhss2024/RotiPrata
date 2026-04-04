package com.rotiprata.api.dto;

import java.util.UUID;

public record LessonMediaStartResponse(UUID assetId, String status, String pollUrl) {}

package com.rotiprata.api.dto;

public record ContentMediaStatusResponse(
    String status,
    String hlsUrl,
    String thumbnailUrl,
    String errorMessage
) {}

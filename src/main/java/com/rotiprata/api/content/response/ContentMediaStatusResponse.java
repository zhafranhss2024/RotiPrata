package com.rotiprata.api.content.dto;

public record ContentMediaStatusResponse(
    String status,
    String hlsUrl,
    String thumbnailUrl,
    String errorMessage
) {}

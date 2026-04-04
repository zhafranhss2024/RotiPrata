package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LessonMediaStartLinkRequest(
    @NotBlank String sourceUrl,
    @NotBlank String mediaKind
) {}

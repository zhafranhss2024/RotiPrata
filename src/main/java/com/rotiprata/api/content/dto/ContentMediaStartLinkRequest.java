package com.rotiprata.api.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ContentMediaStartLinkRequest(@NotBlank String sourceUrl) {}

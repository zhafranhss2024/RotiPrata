package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ContentMediaStartLinkRequest(@NotBlank String sourceUrl) {}

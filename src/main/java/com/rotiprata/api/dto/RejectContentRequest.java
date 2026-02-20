package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectContentRequest(@NotBlank String feedback) {}

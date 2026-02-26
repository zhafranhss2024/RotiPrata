package com.rotiprata.api.dto;

import com.rotiprata.domain.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ContentSubmitRequest(
    @NotBlank @Size(max = 80) String title,
    @NotBlank @Size(max = 500) String description,
    @NotNull ContentType contentType,
    UUID categoryId,
    @Size(max = 160) String learningObjective,
    @Size(max = 500) String originExplanation,
    @Size(max = 500) String definitionLiteral,
    @Size(max = 500) String definitionUsed,
    @Size(max = 160) String olderVersionReference,
    @NotEmpty List<@NotBlank @Size(max = 30) String> tags
) {}

package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AdminContentUpdateRequest(
    @NotBlank @Size(max = 80) String title,
    @NotBlank @Size(max = 500) String description,
    @NotBlank @Size(max = 160) String learningObjective,
    @NotBlank @Size(max = 500) String originExplanation,
    @NotBlank @Size(max = 500) String definitionLiteral,
    @NotBlank @Size(max = 500) String definitionUsed,
    @NotBlank @Size(max = 160) String olderVersionReference,
    @NotNull UUID categoryId,
    @NotEmpty List<@NotBlank @Size(max = 30) String> tags
) {}

package com.rotiprata.api.dto;

import com.rotiprata.domain.ContentType;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ContentUpdateRequest(
    @Size(max = 80) String title,
    @Size(max = 500) String description,
    ContentType contentType,
    UUID categoryId,
    @Size(max = 160) String learningObjective,
    @Size(max = 500) String originExplanation,
    @Size(max = 500) String definitionLiteral,
    @Size(max = 500) String definitionUsed,
    @Size(max = 160) String olderVersionReference,
    List<@Size(max = 30) String> tags
) {}

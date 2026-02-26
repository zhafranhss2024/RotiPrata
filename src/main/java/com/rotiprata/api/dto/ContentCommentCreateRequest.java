package com.rotiprata.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ContentCommentCreateRequest(
    @NotBlank @Size(max = 2000) String body,
    UUID parentId
) {}

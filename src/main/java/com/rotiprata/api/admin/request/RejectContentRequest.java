package com.rotiprata.api.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Represents the reject content request payload accepted by the API layer.
 */
public record RejectContentRequest(@NotBlank @Size(max = 500) String feedback) {}

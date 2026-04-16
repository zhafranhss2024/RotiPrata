package com.rotiprata.api.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for rejecting content, capturing the moderator's reason for enforcement action.
 */
public record RejectContentRequest(@NotBlank @Size(max = 500) String feedback) {}

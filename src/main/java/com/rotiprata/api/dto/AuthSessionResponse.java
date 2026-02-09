package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthSessionResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn,
    UUID userId,
    String email,
    Boolean requiresEmailConfirmation,
    String message
) {}

package com.rotiprata.api.common.response;

import java.util.Map;

/**
 * Represents the api error response payload returned by the API layer.
 */
public record ApiErrorResponse(
    String code,
    String message,
    Map<String, String> fieldErrors,
    Long retryAfterSeconds
) {}

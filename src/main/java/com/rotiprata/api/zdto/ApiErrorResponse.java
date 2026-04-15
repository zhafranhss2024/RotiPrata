package com.rotiprata.api.zdto;

import java.util.Map;

public record ApiErrorResponse(
    String code,
    String message,
    Map<String, String> fieldErrors,
    Long retryAfterSeconds
) {}

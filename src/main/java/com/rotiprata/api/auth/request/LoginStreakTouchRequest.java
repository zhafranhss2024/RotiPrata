package com.rotiprata.api.auth.request;

/**
 * Represents the login streak touch request payload accepted by the API layer.
 */
public record LoginStreakTouchRequest(
    String timezone
) {}

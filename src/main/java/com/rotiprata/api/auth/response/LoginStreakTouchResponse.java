package com.rotiprata.api.auth.response;

import java.time.LocalDate;

/**
 * Represents the login streak touch response payload returned by the API layer.
 */
public record LoginStreakTouchResponse(
    int currentStreak,
    int longestStreak,
    LocalDate lastActivityDate,
    boolean touchedToday
) {}

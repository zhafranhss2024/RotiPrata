package com.rotiprata.api.auth.response;

import java.time.LocalDate;

public record LoginStreakTouchResponse(
    int currentStreak,
    int longestStreak,
    LocalDate lastActivityDate,
    boolean touchedToday
) {}

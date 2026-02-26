package com.rotiprata.api.dto;

import java.time.LocalDate;

public record LoginStreakTouchResponse(
    int currentStreak,
    int longestStreak,
    LocalDate lastActivityDate,
    boolean touchedToday
) {}

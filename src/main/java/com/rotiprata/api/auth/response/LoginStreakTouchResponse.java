package com.rotiprata.api.zdto;

import java.time.LocalDate;

public record LoginStreakTouchResponse(
    int currentStreak,
    int longestStreak,
    LocalDate lastActivityDate,
    boolean touchedToday
) {}

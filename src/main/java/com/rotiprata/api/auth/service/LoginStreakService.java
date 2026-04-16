package com.rotiprata.api.auth.service;

import com.rotiprata.api.auth.response.LoginStreakTouchResponse;
import java.util.UUID;

public interface LoginStreakService {

    /**
     * Updates and returns the current login streak state.
     */
    LoginStreakTouchResponse touchLoginStreak(UUID userId, String accessToken, String requestedTimezone);
}

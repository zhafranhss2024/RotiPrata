package com.rotiprata.api.user.service;

import com.rotiprata.api.user.domain.Profile;
import com.rotiprata.api.user.dto.UserBadgeResponse;
import com.rotiprata.api.user.preference.ThemePreference;
import com.rotiprata.api.user.response.LeaderboardResponse;
import com.rotiprata.security.authorization.AppRole;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Defines the user service operations exposed to the API layer.
 */
public interface UserService {

    /**
     * Returns the current profile for the user.
     */
    Profile getProfile(UUID userId, String accessToken);

    /**
     * Checks whether a display name is already taken.
     */
    boolean isDisplayNameTaken(String displayName);

    /**
     * Returns or creates a profile from JWT claims.
     */
    Profile getOrCreateProfileFromJwt(Jwt jwt, String accessToken);

    /**
     * Returns or creates a profile from explicit identity data.
     */
    Profile getOrCreateProfile(UUID userId, String email, String displayNameHint, Boolean isGenAlpha, String accessToken);

    /**
     * Ensures a profile exists through the user-scoped client.
     */
    Profile ensureProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken, boolean allowSuffix);

    /**
     * Ensures a profile exists through the admin-scoped client.
     */
    Profile ensureProfileWithServiceRole(UUID userId, String displayName, Boolean isGenAlpha, boolean allowSuffix);

    /**
     * Returns the roles assigned to the user.
     */
    List<AppRole> getRoles(UUID userId, String accessToken);

    /**
     * Updates the user's theme preference.
     */
    Profile updateThemePreference(UUID userId, ThemePreference preference, String accessToken);

    /**
     * Updates profile fields that are user-editable.
     */
    Profile updateProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken);

    /**
     * Returns earned user badges.
     */
    List<UserBadgeResponse> getUserBadges(UUID userId, String accessToken);

    /**
     * Returns the leaderboard view for the user.
     */
    LeaderboardResponse getLeaderboard(UUID currentUserId, int page, int pageSize, String query, String accessToken);

    /**
     * Validates the display name format.
     */
    boolean isDisplayNameFormatValid(String displayName);

    /**
     * Normalizes a display name for storage and comparisons.
     */
    String normalizeDisplayName(String displayName);
}

package com.rotiprata.api.user.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.user.domain.Profile;
import com.rotiprata.api.user.domain.UserRole;
import com.rotiprata.api.user.dto.UserBadgeResponse;
import com.rotiprata.api.user.response.LeaderboardEntryResponse;
import com.rotiprata.api.user.response.LeaderboardResponse;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.api.user.preference.ThemePreference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import com.rotiprata.security.SecurityUtils;

/**
 * Implements the user service workflows and persistence coordination used by the API layer.
 */
@Service
public class UserService {
    private static final TypeReference<List<Profile>> PROFILE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UserRole>> USER_ROLE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int DEFAULT_LEADERBOARD_PAGE_SIZE = 20;
    private static final int MAX_LEADERBOARD_PAGE_SIZE = 50;

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;

    /**
     * Creates a user service instance with its collaborators.
     */
    public UserService(SupabaseRestClient supabaseRestClient, SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    /**
     * Returns the profile.
     */
    public Profile getProfile(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Profile> profiles = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            token,
            PROFILE_LIST
        );
        if (profiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return profiles.get(0);
    }

    /**
     * Checks whether display name taken.
     */
    public boolean isDisplayNameTaken(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }
        String normalized = normalizeDisplayName(displayName);
        try {
            List<Profile> profiles = supabaseRestClient.getList(
                "display_name_registry",
                buildQuery(Map.of(
                    "display_name", "eq." + normalized,
                    "select", "display_name"
                )),
                null,
                PROFILE_LIST
            );
            return !profiles.isEmpty();
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                || ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                log.warn("Display name availability check skipped due to RLS restrictions");
                return false;
            }
            throw ex;
        }
    }

    /**
     * Returns the or create profile from jwt.
     */
    public Profile getOrCreateProfileFromJwt(org.springframework.security.oauth2.jwt.Jwt jwt, String accessToken) {
        UUID userId = SecurityUtils.getUserId(jwt);
        String email = jwt.getClaimAsString("email");
        Map<String, Object> metadata = jwt.getClaim("user_metadata");

        String displayName = null;
        Boolean isGenAlpha = null;
        if (metadata != null) {
            Object displayNameValue = metadata.get("display_name");
            if (displayNameValue == null) {
                displayNameValue = metadata.get("preferred_username");
            }
            if (displayNameValue == null) {
                displayNameValue = metadata.get("full_name");
            }
            if (displayNameValue != null) {
                displayName = displayNameValue.toString();
            }

            Object genAlphaValue = metadata.get("is_gen_alpha");
            if (genAlphaValue instanceof Boolean value) {
                isGenAlpha = value;
            }
        }

        return getOrCreateProfile(userId, email, displayName, isGenAlpha, accessToken);
    }

    /**
     * Returns the or create profile.
     */
    public Profile getOrCreateProfile(UUID userId, String email, String displayNameHint, Boolean isGenAlpha, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Profile> profiles = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            token,
            PROFILE_LIST
        );
        if (!profiles.isEmpty()) {
            return profiles.get(0);
        }

        String displayName = buildUniqueDisplayName(displayNameHint, email);
        Profile created = createProfile(userId, displayName, isGenAlpha, token, true);
        ensureUserRole(userId, token);
        return created;
    }

    /**
     * Ensures the profile.
     */
    public Profile ensureProfile(
        UUID userId,
        String displayName,
        Boolean isGenAlpha,
        String accessToken,
        boolean allowSuffix
    ) {
        String token = requireAccessToken(accessToken);
        List<Profile> existing = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            token,
            PROFILE_LIST
        );
        if (!existing.isEmpty()) {
            Profile profile = existing.get(0);
            Map<String, Object> patch = new HashMap<>();
            if (displayName != null && !displayName.isBlank() && !displayName.equals(profile.getDisplayName())) {
                patch.put("display_name", displayName);
            }
            boolean normalizedIsGenAlpha = isGenAlpha != null && isGenAlpha;
            if (profile.getGenAlpha() == null || profile.getGenAlpha() != normalizedIsGenAlpha) {
                patch.put("is_gen_alpha", normalizedIsGenAlpha);
            }
            if (!patch.isEmpty()) {
                List<Profile> updated = supabaseRestClient.patchList(
                    "profiles",
                    buildQuery(Map.of("user_id", "eq." + userId)),
                    patch,
                    token,
                    PROFILE_LIST
                );
                if (!updated.isEmpty()) {
                    profile = updated.get(0);
                }
            }
            ensureUserRole(userId, token);
            return profile;
        }

        Profile created = createProfile(userId, displayName, isGenAlpha, token, allowSuffix);
        ensureUserRole(userId, token);
        return created;
    }

    /**
     * Ensures the profile with service role.
     */
    public Profile ensureProfileWithServiceRole(UUID userId, String displayName, Boolean isGenAlpha, boolean allowSuffix) {
        List<Profile> existing = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            PROFILE_LIST
        );
        if (!existing.isEmpty()) {
            Profile profile = existing.get(0);
            Map<String, Object> patch = new HashMap<>();
            if (displayName != null && !displayName.isBlank() && !displayName.equals(profile.getDisplayName())) {
                patch.put("display_name", displayName);
            }
            boolean normalizedIsGenAlpha = isGenAlpha != null && isGenAlpha;
            if (profile.getGenAlpha() == null || profile.getGenAlpha() != normalizedIsGenAlpha) {
                patch.put("is_gen_alpha", normalizedIsGenAlpha);
            }
            if (!patch.isEmpty()) {
                List<Profile> updated = supabaseAdminRestClient.patchList(
                    "profiles",
                    buildQuery(Map.of("user_id", "eq." + userId)),
                    patch,
                    PROFILE_LIST
                );
                if (!updated.isEmpty()) {
                    profile = updated.get(0);
                }
            }
            ensureUserRoleWithServiceRole(userId);
            return profile;
        }

        Profile created = createProfileWithServiceRole(userId, displayName, isGenAlpha, allowSuffix);
        ensureUserRoleWithServiceRole(userId);
        return created;
    }


    /**
     * Returns the roles.
     */
    public List<AppRole> getRoles(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<UserRole> roles = supabaseRestClient.getList(
            "user_roles",
            buildQuery(Map.of(
                "user_id", "eq." + userId,
                "select", "role"
            )),
            token,
            USER_ROLE_LIST
        );
        return roles.stream().map(UserRole::getRole).toList();
    }

    /**
     * Updates the theme preference.
     */
    public Profile updateThemePreference(UUID userId, ThemePreference preference, String accessToken) {
        String token = requireAccessToken(accessToken);
        Map<String, Object> patch = new HashMap<>();
        patch.put("theme_preference", preference.toJson());
        List<Profile> updated = supabaseRestClient.patchList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            patch,
            token,
            PROFILE_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return updated.get(0);
    }

    /**
     * Updates the profile.
     */
    public Profile updateProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken) {
        String token = requireAccessToken(accessToken);
        Profile current = getProfile(userId, token);

        Map<String, Object> patch = new HashMap<>();
        if (displayName != null) {
            String normalizedDisplayName = normalizeDisplayName(displayName);
            String currentDisplayName = normalizeDisplayName(current.getDisplayName());
            if (!isDisplayNameFormatValid(displayName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name format is invalid");
            }
            if (normalizedDisplayName != null && !normalizedDisplayName.equals(currentDisplayName) && isDisplayNameTaken(normalizedDisplayName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Display name already in use");
            }
            patch.put("display_name", normalizedDisplayName);
        }
        if (isGenAlpha != null) {
            patch.put("is_gen_alpha", isGenAlpha);
        }

        if (patch.isEmpty()) {
            return current;
        }

        patch.put("updated_at", OffsetDateTime.now());
        List<Profile> updated = supabaseRestClient.patchList(
            "profiles",
            buildQuery(Map.of("user_id", "eq." + userId)),
            patch,
            token,
            PROFILE_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return updated.get(0);
    }

    /**
     * Returns the user badges.
     */
    public List<UserBadgeResponse> getUserBadges(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);

        List<Map<String, Object>> rewardRows = supabaseAdminRestClient.getList(
            "user_lesson_rewards",
            buildQuery(Map.of(
                "select", "lesson_id,badge_name,awarded_at",
                "user_id", "eq." + userId,
                "order", "awarded_at.desc"
            )),
            MAP_LIST
        );

        LinkedHashMap<String, Map<String, Object>> rewardByLessonId = new LinkedHashMap<>();
        Set<String> earnedLessonIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rewardRows) {
            String lessonId = stringValue(row.get("lesson_id"));
            if (lessonId == null || lessonId.isBlank() || rewardByLessonId.containsKey(lessonId)) {
                continue;
            }
            rewardByLessonId.put(lessonId, row);
            earnedLessonIds.add(lessonId);
        }

        Map<String, Map<String, Object>> earnedLessonMeta = fetchLessonsByIds(earnedLessonIds);

        List<Map<String, Object>> publishedBadgeLessons = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id,title,badge_name,badge_icon_url",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "is_published", "eq.true",
                "badge_name", "not.is.null",
                "order", "title.asc"
            )),
            MAP_LIST
        );

        List<UserBadgeResponse> earnedBadges = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : rewardByLessonId.entrySet()) {
            String lessonId = entry.getKey();
            Map<String, Object> reward = entry.getValue();
            Map<String, Object> lesson = earnedLessonMeta.get(lessonId);
            String badgeName = stringValue(lesson == null ? null : lesson.get("badge_name"));
            if (badgeName == null) {
                badgeName = stringValue(reward.get("badge_name"));
            }
            if (badgeName == null || badgeName.isBlank()) {
                continue;
            }
            earnedBadges.add(
                new UserBadgeResponse(
                    parseUuid(lessonId),
                    stringValue(lesson == null ? null : lesson.get("title")),
                    badgeName,
                    stringValue(lesson == null ? null : lesson.get("badge_icon_url")),
                    true,
                    parseOffsetDateTime(reward.get("awarded_at"))
                )
            );
        }

        List<UserBadgeResponse> lockedBadges = new ArrayList<>();
        for (Map<String, Object> lesson : publishedBadgeLessons) {
            String lessonId = stringValue(lesson.get("id"));
            if (lessonId == null || earnedLessonIds.contains(lessonId)) {
                continue;
            }
            String badgeName = stringValue(lesson.get("badge_name"));
            if (badgeName == null || badgeName.isBlank()) {
                continue;
            }
            lockedBadges.add(
                new UserBadgeResponse(
                    parseUuid(lessonId),
                    stringValue(lesson.get("title")),
                    badgeName,
                    stringValue(lesson.get("badge_icon_url")),
                    false,
                    null
                )
            );
        }

        earnedBadges.sort(Comparator.comparing(UserBadgeResponse::earnedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        lockedBadges.sort(Comparator.comparing(UserBadgeResponse::badgeName, Comparator.nullsLast(String::compareToIgnoreCase)));

        List<UserBadgeResponse> allBadges = new ArrayList<>(earnedBadges.size() + lockedBadges.size());
        allBadges.addAll(earnedBadges);
        allBadges.addAll(lockedBadges);
        return allBadges;
    }

    /**
     * Returns the leaderboard.
     */
    public LeaderboardResponse getLeaderboard(UUID currentUserId, int page, int pageSize, String query, String accessToken) {
        requireAccessToken(accessToken);

        int normalizedPageSize = normalizeLeaderboardPageSize(pageSize);
        String normalizedQuery = normalizeLeaderboardQuery(query);
        Set<UUID> adminUserIds = fetchAdminUserIds();

        List<Profile> sortedProfiles = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of("select", "user_id,display_name,avatar_url,reputation_points,current_streak")),
            PROFILE_LIST
        ).stream()
            .filter(profile -> profile.getUserId() != null)
            .filter(profile -> !adminUserIds.contains(profile.getUserId()))
            .sorted(leaderboardProfileComparator())
            .toList();

        List<LeaderboardEntryResponse> rankedEntries = new ArrayList<>(sortedProfiles.size());
        Integer previousXp = null;
        int currentRank = 0;
        for (int index = 0; index < sortedProfiles.size(); index += 1) {
            Profile profile = sortedProfiles.get(index);
            int xp = normalizedXp(profile.getReputationPoints());
            if (previousXp == null || previousXp != xp) {
                currentRank = index + 1;
                previousXp = xp;
            }
            rankedEntries.add(
                new LeaderboardEntryResponse(
                    currentRank,
                    profile.getUserId(),
                    normalizeLeaderboardDisplayName(profile.getDisplayName()),
                    profile.getAvatarUrl(),
                    xp,
                    normalizedXp(profile.getCurrentStreak()),
                    profile.getUserId().equals(currentUserId)
                )
            );
        }

        LeaderboardEntryResponse currentUser = rankedEntries.stream()
            .filter(entry -> entry.userId().equals(currentUserId))
            .findFirst()
            .orElse(null);

        List<LeaderboardEntryResponse> filteredEntries = rankedEntries.stream()
            .filter(entry -> matchesLeaderboardQuery(entry, normalizedQuery))
            .toList();

        int totalCount = filteredEntries.size();
        int maxPage = totalCount == 0 ? 1 : (int) Math.ceil(totalCount / (double) normalizedPageSize);
        int normalizedPage = Math.max(1, Math.min(page, maxPage));
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, totalCount);
        int toIndex = Math.min(fromIndex + normalizedPageSize, totalCount);
        boolean hasNext = normalizedPage < maxPage;

        return new LeaderboardResponse(
            filteredEntries.subList(fromIndex, toIndex),
            normalizedPage,
            normalizedPageSize,
            hasNext,
            totalCount,
            normalizedQuery == null ? "" : normalizedQuery,
            currentUser
        );
    }

    /**
     * Creates the profile.
     */
    private Profile createProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken) {
        return createProfile(userId, displayName, isGenAlpha, accessToken, true);
    }

    /**
     * Creates the profile with service role.
     */
    private Profile createProfileWithServiceRole(UUID userId, String displayName, Boolean isGenAlpha, boolean allowSuffix) {
        String candidate = displayName;
        try {
            return insertProfileWithServiceRole(userId, candidate, isGenAlpha);
        } catch (ResponseStatusException ex) {
            if (allowSuffix && ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                String suffix = userId.toString().substring(0, 6);
                String fallback = candidate + "-" + suffix;
                if (!fallback.equals(candidate)) {
                    return insertProfileWithServiceRole(userId, fallback, isGenAlpha);
                }
            }
            throw ex;
        }
    }

    /**
     * Creates the profile.
     */
    private Profile createProfile(
        UUID userId,
        String displayName,
        Boolean isGenAlpha,
        String accessToken,
        boolean allowSuffix
    ) {
        String candidate = displayName;
        try {
            return insertProfile(userId, candidate, isGenAlpha, accessToken);
        } catch (ResponseStatusException ex) {
            if (allowSuffix && ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                String suffix = userId.toString().substring(0, 6);
                String fallback = candidate + "-" + suffix;
                if (!fallback.equals(candidate)) {
                    return insertProfile(userId, fallback, isGenAlpha, accessToken);
                }
            }
            throw ex;
        }
    }

    /**
     * Handles insert profile.
     */
    private Profile insertProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken) {
        Map<String, Object> insert = new HashMap<>();
        insert.put("user_id", userId);
        insert.put("display_name", displayName);
        insert.put("is_gen_alpha", isGenAlpha != null && isGenAlpha);
        insert.put("created_at", OffsetDateTime.now());
        insert.put("updated_at", OffsetDateTime.now());

        List<Profile> created = supabaseRestClient.postList("profiles", insert, accessToken, PROFILE_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create profile");
        }
        return created.get(0);
    }

    /**
     * Ensures the user role.
     */
    private void ensureUserRole(UUID userId, String accessToken) {
        List<UserRole> roles = supabaseRestClient.getList(
            "user_roles",
            buildQuery(Map.of(
                "user_id", "eq." + userId,
                "select", "id"
            )),
            accessToken,
            USER_ROLE_LIST
        );
        if (!roles.isEmpty()) {
            return;
        }
        Map<String, Object> insert = new HashMap<>();
        insert.put("user_id", userId);
        insert.put("role", AppRole.USER.toJson());
        supabaseRestClient.postList("user_roles", insert, accessToken, USER_ROLE_LIST);
    }

    /**
     * Handles insert profile with service role.
     */
    private Profile insertProfileWithServiceRole(UUID userId, String displayName, Boolean isGenAlpha) {
        Map<String, Object> insert = new HashMap<>();
        insert.put("user_id", userId);
        insert.put("display_name", displayName);
        insert.put("is_gen_alpha", isGenAlpha != null && isGenAlpha);
        insert.put("created_at", OffsetDateTime.now());
        insert.put("updated_at", OffsetDateTime.now());

        List<Profile> created = supabaseAdminRestClient.postList("profiles", insert, PROFILE_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create profile");
        }
        return created.get(0);
    }

    /**
     * Ensures the user role with service role.
     */
    private void ensureUserRoleWithServiceRole(UUID userId) {
        List<UserRole> roles = supabaseAdminRestClient.getList(
            "user_roles",
            buildQuery(Map.of(
                "user_id", "eq." + userId,
                "select", "id"
            )),
            USER_ROLE_LIST
        );
        if (!roles.isEmpty()) {
            return;
        }
        Map<String, Object> insert = new HashMap<>();
        insert.put("user_id", userId);
        insert.put("role", AppRole.USER.toJson());
        supabaseAdminRestClient.postList("user_roles", insert, USER_ROLE_LIST);
    }


    /**
     * Builds the unique display name.
     */
    private String buildUniqueDisplayName(String displayNameHint, String email) {
        String base = null;
        if (displayNameHint != null && !displayNameHint.isBlank()) {
            base = displayNameHint.trim();
        } else if (email != null && email.contains("@")) {
            base = email.substring(0, email.indexOf('@'));
        }

        if (base == null || base.isBlank()) {
            base = "user";
        }

        base = base.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (base.isBlank()) {
            base = "user";
        }

        return base;
    }

    /**
     * Checks whether display name format valid.
     */
    public boolean isDisplayNameFormatValid(String displayName) {
        if (displayName == null) {
            return false;
        }
        String trimmed = displayName.trim();
        if (trimmed.length() < 3 || trimmed.length() > 30) {
            return false;
        }
        return trimmed.matches("^[a-zA-Z0-9._-]+$");
    }

    /**
     * Normalizes the display name.
     */
    public String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return displayName.trim().toLowerCase();
    }

    /**
     * Builds the query.
     */
    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    /**
     * Requires the access token.
     */
    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    /**
     * Fetches the admin user ids.
     */
    private Set<UUID> fetchAdminUserIds() {
        List<UserRole> roles = supabaseAdminRestClient.getList(
            "user_roles",
            buildQuery(Map.of(
                "select", "user_id,role",
                "role", "eq." + AppRole.ADMIN.toJson()
            )),
            USER_ROLE_LIST
        );
        Set<UUID> adminUserIds = new LinkedHashSet<>();
        for (UserRole role : roles) {
            if (role != null && role.getUserId() != null) {
                adminUserIds.add(role.getUserId());
            }
        }
        return adminUserIds;
    }

    /**
     * Handles leaderboard profile comparator.
     */
    private Comparator<Profile> leaderboardProfileComparator() {
        return Comparator
            .comparingInt((Profile profile) -> normalizedXp(profile.getReputationPoints()))
            .reversed()
            .thenComparing(
                profile -> normalizeLeaderboardDisplayName(profile.getDisplayName()),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            )
            .thenComparing(profile -> profile.getUserId().toString());
    }

    /**
     * Handles matches leaderboard query.
     */
    private boolean matchesLeaderboardQuery(LeaderboardEntryResponse entry, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        String displayName = entry.displayName();
        return displayName != null && displayName.toLowerCase().contains(normalizedQuery);
    }

    /**
     * Normalizes the leaderboard display name.
     */
    private String normalizeLeaderboardDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Normalizes the leaderboard page size.
     */
    private int normalizeLeaderboardPageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_LEADERBOARD_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LEADERBOARD_PAGE_SIZE);
    }

    /**
     * Normalizes the leaderboard query.
     */
    private String normalizeLeaderboardQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim().toLowerCase();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Normalizes the d xp.
     */
    private int normalizedXp(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    /**
     * Fetches the lessons by ids.
     */
    private Map<String, Map<String, Object>> fetchLessonsByIds(Set<String> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> lessonRows = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id,title,badge_name,badge_icon_url",
                "id", "in.(" + String.join(",", lessonIds) + ")"
            )),
            MAP_LIST
        );
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : lessonRows) {
            String lessonId = stringValue(row.get("id"));
            if (lessonId != null) {
                byId.put(lessonId, row);
            }
        }
        return byId;
    }

    /**
     * Extracts a string value from a mixed payload field.
     */
    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Parses the uuid.
     */
    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Parses the offset date time.
     */
    private OffsetDateTime parseOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        try {
            return OffsetDateTime.parse(Objects.toString(value, null));
        } catch (RuntimeException ex) {
            return null;
        }
    }

}

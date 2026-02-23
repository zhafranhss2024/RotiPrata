package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.domain.AppRole;
import com.rotiprata.domain.Profile;
import com.rotiprata.domain.ThemePreference;
import com.rotiprata.domain.UserRole;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import com.rotiprata.security.SecurityUtils;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class UserService {
    private static final TypeReference<List<Profile>> PROFILE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UserRole>> USER_ROLE_LIST = new TypeReference<>() {};
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final SupabaseRestClient supabaseRestClient;

    public UserService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

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
            boolean needsUpdate = false;
            Map<String, Object> patch = new HashMap<>();
            if (profile.getDisplayName() == null) {
                patch.put("display_name", displayName);
                needsUpdate = true;
            }
            if (needsUpdate) {
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

    private Profile createProfile(UUID userId, String displayName, Boolean isGenAlpha, String accessToken) {
        return createProfile(userId, displayName, isGenAlpha, accessToken, true);
    }

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

    public String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return displayName.trim().toLowerCase();
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

}

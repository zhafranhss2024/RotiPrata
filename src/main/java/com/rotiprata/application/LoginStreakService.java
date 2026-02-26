package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.LoginStreakTouchResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LoginStreakService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final SupabaseRestClient supabaseRestClient;

    public LoginStreakService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public LoginStreakTouchResponse touchLoginStreak(UUID userId, String accessToken, String requestedTimezone) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }

        List<Map<String, Object>> profiles = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("select", "id,current_streak,longest_streak,last_activity_date,timezone", "user_id", "eq." + userId, "limit", "1")),
            token,
            MAP_LIST
        );
        if (profiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        Map<String, Object> profile = profiles.get(0);
        String profileId = stringValue(profile.get("id"));
        if (profileId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        ZoneId resolvedTimezone = resolveTimezone(requestedTimezone, stringValue(profile.get("timezone")));
        LocalDate today = LocalDate.now(resolvedTimezone);
        LocalDate lastActivityDate = parseLocalDate(profile.get("last_activity_date"));
        int currentStreak = parseInteger(profile.get("current_streak")) == null ? 0 : parseInteger(profile.get("current_streak"));
        int longestStreak = parseInteger(profile.get("longest_streak")) == null ? 0 : parseInteger(profile.get("longest_streak"));
        boolean touchedToday = lastActivityDate != null && lastActivityDate.equals(today);

        int nextCurrentStreak = currentStreak;
        int nextLongestStreak = longestStreak;
        if (!touchedToday) {
            if (lastActivityDate != null && lastActivityDate.plusDays(1).equals(today)) {
                nextCurrentStreak = Math.max(1, currentStreak + 1);
            } else {
                nextCurrentStreak = 1;
            }
            nextLongestStreak = Math.max(longestStreak, nextCurrentStreak);
        }

        String normalizedRequestedTimezone = normalizeTimezone(requestedTimezone);
        String normalizedStoredTimezone = normalizeTimezone(stringValue(profile.get("timezone")));

        Map<String, Object> patch = new LinkedHashMap<>();
        if (!touchedToday) {
            patch.put("current_streak", nextCurrentStreak);
            patch.put("longest_streak", nextLongestStreak);
            patch.put("last_activity_date", today.toString());
        }
        if (normalizedRequestedTimezone != null && !normalizedRequestedTimezone.equals(normalizedStoredTimezone)) {
            patch.put("timezone", normalizedRequestedTimezone);
        }
        if (!patch.isEmpty()) {
            patch.put("updated_at", OffsetDateTime.now());
            patchProfileWithTimezoneFallback(profileId, patch, token);
        }

        return new LoginStreakTouchResponse(
            nextCurrentStreak,
            nextLongestStreak,
            touchedToday ? lastActivityDate : today,
            touchedToday
        );
    }

    private void patchProfileWithTimezoneFallback(String profileId, Map<String, Object> patch, String token) {
        try {
            supabaseRestClient.patchList(
                "profiles",
                buildQuery(Map.of("id", "eq." + profileId)),
                patch,
                token,
                MAP_LIST
            );
        } catch (ResponseStatusException ex) {
            if (!patch.containsKey("timezone") || !isMissingTimezoneColumn(ex)) {
                throw ex;
            }
            Map<String, Object> withoutTimezone = new LinkedHashMap<>(patch);
            withoutTimezone.remove("timezone");
            if (withoutTimezone.size() <= 1) {
                return;
            }
            supabaseRestClient.patchList(
                "profiles",
                buildQuery(Map.of("id", "eq." + profileId)),
                withoutTimezone,
                token,
                MAP_LIST
            );
        }
    }

    private boolean isMissingTimezoneColumn(ResponseStatusException ex) {
        if (!(ex.getCause() instanceof RestClientResponseException responseException)) {
            return false;
        }
        String body = responseException.getResponseBodyAsString();
        if (body == null) {
            return false;
        }
        String normalized = body.toLowerCase();
        return normalized.contains("timezone") && normalized.contains("column");
    }

    private ZoneId resolveTimezone(String requestedTimezone, String storedTimezone) {
        ZoneId requested = parseZoneId(requestedTimezone);
        if (requested != null) {
            return requested;
        }
        ZoneId stored = parseZoneId(storedTimezone);
        if (stored != null) {
            return stored;
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private String normalizeTimezone(String timezone) {
        ZoneId zoneId = parseZoneId(timezone);
        return zoneId == null ? null : zoneId.getId();
    }

    private ZoneId parseZoneId(String timezone) {
        String normalized = stringValue(timezone);
        if (normalized == null) {
            return null;
        }
        try {
            return ZoneId.of(normalized);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(401), "Missing access token");
        }
        return accessToken;
    }
}

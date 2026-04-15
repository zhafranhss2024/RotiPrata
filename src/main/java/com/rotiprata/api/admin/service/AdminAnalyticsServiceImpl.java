package com.rotiprata.api.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.generalutils.DateUtils;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;

/**
 * Implementation of AdminAnalyticsService.
 * Provides concrete logic for fetching flagged content,
 * computing review times, top flagged users/contents,
 * and retrieving audit logs.
 */
@Service
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final ContentService contentService;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final UserService userService;
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsServiceImpl.class);
    
    // Constant
    private static final String CREATED_AT = "created_at";
    private static final String RESOLVED_AT = "resolved_at";
    private static final String DATE = "date";
    private static final String COUNT = "count";

    public AdminAnalyticsServiceImpl(
        ContentService contentService,
        SupabaseAdminRestClient supabaseAdminRestClient,
        UserService userService
    ) {
        this.contentService = contentService;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.userService = userService;
    }

    /** Retrieves flagged content aggregated by day. */
    @Override
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear(
        UUID adminUserId,
        String accessToken,
        String month,
        String year
    ) {
        requireAdmin(adminUserId, accessToken);
        
        // Aggregating the data
        /**
         * {
         *     "2026-04-01": 5
         * }
         */
        
        try {
            List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

            Map<String, Long> countsByDate = rawFlags.stream()
                .map(f -> parseInstant(f, CREATED_AT))
                .filter(Objects::nonNull)
                .map(this::toDateString)
                .collect(Collectors.groupingBy(date -> date, LinkedHashMap::new, Collectors.counting()));

            return countsByDate.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put(DATE, e.getKey());
                    map.put(COUNT, e.getValue());
                    return map;
                })
                .toList();

        } catch (Exception e) {
            log.warn("Failed to fetch flagged content for {}/{}: {}", month, year, e.getMessage(), e);
            return List.of(); 
        }
    }


    /** Computes the average review time for resolved flagged content. */
    @Override
    public double getAverageReviewTimeByMonthAndYear(
        UUID adminUserId,
        String accessToken,
        String month,
        String year
    ) {
        requireAdmin(adminUserId, accessToken);
        try {
            List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

            return rawFlags.stream()
                    .filter(f -> parseInstant(f, CREATED_AT) != null && parseInstant(f, RESOLVED_AT) != null)
                    .map(this::computeReviewMinutes)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);

        } catch (Exception e) {
            log.warn("Failed to compute average review time for {}/{}: {}", month, year, e.getMessage(), e);
            return 0.0;
        }
    }

    /** Retrieves the top users who flagged content. */
    @Override
    public List<Map<String, Object>> getTopFlagUsers(
        UUID adminUserId,
        String accessToken,
        String month,
        String year
    ) {
         requireAdmin(adminUserId, accessToken);
         try {
            return supabaseAdminRestClient.rpcList("get_top_flag_users", DateUtils.buildMonthYearParams(month, year), MAP_LIST);
        } catch (Exception e) {
            log.warn("Failed to fetch top flag users for {}/{}: {}", month, year, e.getMessage(), e);
            return List.of();
        }
    }

    /** Retrieves the top flagged contents. */
    @Override
    public List<Map<String, Object>> getTopFlagContents(
        UUID adminUserId,
        String accessToken,
        String month,
        String year
    ) {
        requireAdmin(adminUserId, accessToken);
        try {
            return supabaseAdminRestClient.rpcList("get_top_flag_content", DateUtils.buildMonthYearParams(month, year), MAP_LIST);
        } catch (Exception e) {
            log.warn("Failed to fetch top flagged content for {}/{}: {}", month, year, e.getMessage(), e);
            return List.of();
        }
    }


    /** Fetches audit logs including user display names. */
    @Override
    public List<Map<String, Object>> getAuditLogs(
        UUID adminUserId,
        String accessToken,
        String month,
        String year
    ) {
        requireAdmin(adminUserId, accessToken);
        try {
            String query = DateUtils.buildDateQuery(month, year);
            String select = "*,profiles(user_id,display_name)";
            return supabaseAdminRestClient.getList("audit_logs", query + "&select=" + select, MAP_LIST);
        } catch (Exception e) {
            log.warn("Failed to fetch audit logs for {}/{}: {}", month, year, e.getMessage(), e);
            return List.of();
        }
    }

    /**Private Helpers */
    /** Parses an ISO-8601 timestamp string from the map into an Instant, or returns null if missing. */
    private Instant parseInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        try {
            return value != null ? Instant.parse((String) value) : null;
        } catch (Exception e) {
            log.warn("Invalid timestamp for key {}: {}", key, value);
            return null;
        }
    }

    /** Converts an Instant to a UTC date string in yyyy-MM-dd format. */
    private String toDateString(Instant instant) {
        return instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString();
    }

    /** Computes the review time in minutes between created and resolved timestamps, or null if either is missing. */
    private Long computeReviewMinutes(Map<String, Object> f) {
        Instant created = parseInstant(f, CREATED_AT);
        Instant resolved = parseInstant(f, RESOLVED_AT);

        if (created == null || resolved == null) return null;

        return Duration.between(created, resolved).toMinutes();
    }

    // Ensures the user is an admin; throws 401 if missing info, 403 if not an admin
    private void requireAdmin(UUID adminUserId, String accessToken) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        if (!userService.getRoles(adminUserId, accessToken).contains(AppRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}

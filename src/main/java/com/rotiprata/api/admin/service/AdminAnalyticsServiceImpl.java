package com.rotiprata.api.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.generalutils.DateUtils;
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
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    // Constant
    private static final String CREATED_AT = "created_at";
    private static final String RESOLVED_AT = "resolved_at";
    private static final String DATE = "date";
    private static final String COUNT = "count";

    public AdminAnalyticsServiceImpl(ContentService contentService, SupabaseAdminRestClient supabaseAdminRestClient) {
        this.contentService = contentService;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    /** Retrieves flagged content aggregated by day. */
    @Override
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear(String accessToken, String month, String year) {
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

        // Aggregating the data
        /**
         * {
         *     "2026-04-01": 5
         * }
         */
        Map<String, Long> countsByDate = rawFlags.stream()
            .map(f -> parseInstant(f, CREATED_AT))
            .filter(Objects::nonNull)
            .map(this::toDateString)
            .collect(Collectors.groupingBy(date -> date, LinkedHashMap::new, Collectors.counting()));

        // Convert to JSON format
        return countsByDate.entrySet().stream()
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put(DATE, e.getKey());
                map.put(COUNT, e.getValue());
                return map;
            })
            .toList();
    }

    /** Computes the average review time for resolved flagged content. */
    @Override
    public double getAverageReviewTimeByMonthAndYear(String accessToken, String month, String year) {
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

        return rawFlags.stream()
                .filter(f -> parseInstant(f, CREATED_AT) != null && parseInstant(f, RESOLVED_AT) != null)
                .map(this::computeReviewMinutes)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    /** Retrieves the top users who flagged content. */
    @Override
    public List<Map<String, Object>> getTopFlagUsers(String month, String year) {
        return supabaseAdminRestClient.rpcList("get_top_flag_users", DateUtils.buildMonthYearParams(month, year), MAP_LIST);
    }

    /** Retrieves the top flagged contents. */
    @Override
    public List<Map<String, Object>> getTopFlagContents(String month, String year) {
        return supabaseAdminRestClient.rpcList("get_top_flag_content", DateUtils.buildMonthYearParams(month, year), MAP_LIST);
    }

    /** Fetches audit logs including user display names. */
    @Override
    public List<Map<String, Object>> getAuditLogs(String month, String year) {
        String query = DateUtils.buildDateQuery(month, year);
        String select = "*,profiles(user_id,display_name)";

        return supabaseAdminRestClient.getList("audit_logs", query + "&select=" + select, MAP_LIST);
    }

    /**Private Helpers */
    /** Parses an ISO-8601 timestamp string from the map into an Instant, or returns null if missing. */
    private Instant parseInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? Instant.parse((String) value) : null;
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
}
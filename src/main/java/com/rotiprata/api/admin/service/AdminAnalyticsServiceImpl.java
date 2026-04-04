package com.rotiprata.api.admin.service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

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

    public AdminAnalyticsServiceImpl(ContentService contentService, SupabaseAdminRestClient supabaseAdminRestClient) {
        this.contentService = contentService;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    /** Retrieves flagged content aggregated by day. */
    @Override
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear(String accessToken, String month, String year) {
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

        Map<String, Long> countsByDate = rawFlags.stream()
            .map(f -> (String) f.get("created_at"))
            .map(c -> c.substring(0, 10))
            .collect(Collectors.groupingBy(date -> date, LinkedHashMap::new, Collectors.counting()));

        return countsByDate.entrySet().stream()
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("date", e.getKey());
                map.put("count", e.getValue().intValue());
                return map;
            })
            .collect(Collectors.toList());
    }

    /** Computes the average review time for resolved flagged content. */
    @Override
    public double getAverageReviewTimeByMonthAndYear(String accessToken, String month, String year) {
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

        List<Long> reviewTimes = rawFlags.stream()
            .filter(f -> f.get("resolved_at") != null)
            .map(f -> {
                long createdMillis = java.time.Instant.parse((String) f.get("created_at")).toEpochMilli();
                long resolvedMillis = java.time.Instant.parse((String) f.get("resolved_at")).toEpochMilli();
                return (resolvedMillis - createdMillis) / (1000 * 60); // minutes
            })
            .toList();

        return reviewTimes.isEmpty() ? 0 : reviewTimes.stream().mapToLong(Long::longValue).average().orElse(0);
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
}
package com.rotiprata.application;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class AdminAnalyticsService {

    private final ContentService contentService;
    private final SupabaseRestClient supabaseRestClient;
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    public AdminAnalyticsService(ContentService contentService, SupabaseRestClient supabaseRestClient) {
        this.contentService = contentService;
        this.supabaseRestClient = supabaseRestClient;
    }
    
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear (String accessToken, String month, String year) {
        
        // Fetch raw flagged content
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);
       
        // Aggregate counts by date
        Map<String, Long> countsByDate = rawFlags.stream()
            .map(f -> (String) f.get("created_at"))
            .map(createdAt -> createdAt.substring(0, 10)) // keep only YYYY-MM-DD
            .collect(Collectors.groupingBy(date -> date, LinkedHashMap::new, Collectors.counting()));

        // Convert to List<Map<String,Object>> format
        List<Map<String, Object>> aggregated = countsByDate.entrySet().stream()
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("date", e.getKey());
                map.put("count", e.getValue().intValue());
                return map;
            })
            .collect(Collectors.toList());

        return aggregated;
    }

    public double getAverageReviewTimeByMonthAndYear(String accessToken, String month, String year) {
        // Fetch raw flagged content
        List<Map<String, Object>> rawFlags = contentService.getFlaggedContentByMonthAndYear(accessToken, month, year);

        // Filter resolved flags and compute time difference in minutes
        List<Long> reviewTimes = rawFlags.stream()
            .filter(f -> f.get("resolved_at") != null)
            .map(f -> {
                String created = (String) f.get("created_at");
                String resolved = (String) f.get("resolved_at");

                long createdMillis = java.time.Instant.parse(created).toEpochMilli();
                long resolvedMillis = java.time.Instant.parse(resolved).toEpochMilli();

                return (resolvedMillis - createdMillis) / (1000 * 60); // minutes
            })
            .toList();

        // Compute average
        if (reviewTimes.isEmpty()) return 0;
        return reviewTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public List<Map<String, Object>> getTopFlagUsers(String accessToken, String month, String year) {
        return supabaseRestClient.rpcList(
            "get_top_flag_users",
            DateUtils.buildMonthYearParams(month, year),
            accessToken,
            MAP_LIST
        );
    }

    public List<Map<String, Object>> getTopFlagContents(String accessToken, String month, String year) {
        return supabaseRestClient.rpcList(
            "get_top_flag_content",
            DateUtils.buildMonthYearParams(month, year),
            accessToken,
            MAP_LIST
        );
    }

    public List<Map<String, Object>> getAuditLogs(String accessToken, String month, String year) {
        String query = DateUtils.buildDateQuery(month, year);
        // Join profiles table to get display_name
        String select = "*,profiles(display_name)";
        
        List<Map<String, Object>> result =
        supabaseRestClient.getList(
            "audit_logs",
            query + "&select=" + select,  
            accessToken,
            MAP_LIST
        );

        return result;
    }
}

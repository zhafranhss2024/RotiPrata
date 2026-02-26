package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentEngagementService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private final SupabaseRestClient supabaseRestClient;

    public ContentEngagementService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<Map<String, Object>> decorateItemsWithUserEngagement(
        List<Map<String, Object>> items,
        UUID userId,
        String accessToken
    ) {
        if (items == null || items.isEmpty()) {
            return items;
        }

        if (userId == null || accessToken == null || accessToken.isBlank()) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                item.put("is_liked", false);
                item.put("is_saved", false);
            }
            return items;
        }

        List<String> contentIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String id = stringValue(item.get("id"));
            if (id != null && !id.isBlank()) {
                contentIds.add(id);
            }
        }
        if (contentIds.isEmpty()) {
            return items;
        }

        Set<String> liked = fetchContentIds("content_likes", userId, contentIds, accessToken);
        Set<String> saved = fetchContentIds("content_saves", userId, contentIds, accessToken);

        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String id = stringValue(item.get("id"));
            if (id == null) {
                continue;
            }
            item.put("is_liked", liked.contains(id));
            item.put("is_saved", saved.contains(id));
        }

        return items;
    }

    private Set<String> fetchContentIds(
        String table,
        UUID userId,
        List<String> contentIds,
        String accessToken
    ) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Set.of();
        }
        String idList = String.join(",", new LinkedHashSet<>(contentIds));
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            table,
            buildQuery(Map.of(
                "select", "content_id",
                "user_id", "eq." + userId,
                "content_id", "in.(" + idList + ")"
            )),
            accessToken,
            MAP_LIST
        );
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String id = stringValue(row.get("content_id"));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}

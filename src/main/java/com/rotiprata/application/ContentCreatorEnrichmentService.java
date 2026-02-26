package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentCreatorEnrichmentService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseAdminRestClient;

    public ContentCreatorEnrichmentService(SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    public List<Map<String, Object>> enrichWithCreatorProfiles(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }

        Set<UUID> creatorIds = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            UUID creatorId = parseUuid(item == null ? null : item.get("creator_id"));
            if (creatorId != null) {
                creatorIds.add(creatorId);
            }
        }

        if (creatorIds.isEmpty()) {
            return items;
        }

        Map<UUID, Map<String, Object>> profileByUserId = fetchProfilesByUserId(creatorIds);
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            UUID creatorId = parseUuid(item.get("creator_id"));
            if (creatorId == null) {
                continue;
            }
            Map<String, Object> profile = profileByUserId.get(creatorId);
            if (profile != null) {
                item.put("creator", profile);
            }
        }

        return items;
    }

    private Map<UUID, Map<String, Object>> fetchProfilesByUserId(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        String inClause = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<Map<String, Object>> profileRows = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of(
                "select", "user_id,display_name,avatar_url",
                "user_id", "in.(" + inClause + ")"
            )),
            MAP_LIST
        );

        Map<UUID, Map<String, Object>> byUserId = new LinkedHashMap<>();
        for (Map<String, Object> profile : profileRows) {
            UUID userId = parseUuid(profile.get("user_id"));
            if (userId == null) {
                continue;
            }
            Map<String, Object> creator = new LinkedHashMap<>();
            creator.put("user_id", userId);
            creator.put("display_name", normalizeNullableText(toStringOrNull(profile.get("display_name"))));
            creator.put("avatar_url", normalizeNullableText(toStringOrNull(profile.get("avatar_url"))));
            byUserId.put(userId, creator);
        }
        return byUserId;
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
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

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

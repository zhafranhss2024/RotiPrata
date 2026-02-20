package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TagService {
    private static final TypeReference<List<Map<String, Object>>> TAG_ROWS = new TypeReference<>() {};
    private static final int DEFAULT_LIMIT = 20;

    private final SupabaseAdminRestClient adminRestClient;

    public TagService(SupabaseAdminRestClient adminRestClient) {
        this.adminRestClient = adminRestClient;
    }

    public List<String> searchTags(String query) {
        String normalized = query == null ? "" : query.trim();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", "tag");
        if (!normalized.isBlank()) {
            params.put("tag", "ilike.*" + escapeForIlike(normalized) + "*");
        }
        params.put("order", "tag.asc");
        params.put("limit", String.valueOf(DEFAULT_LIMIT));

        List<Map<String, Object>> rows = adminRestClient.getList(
            "content_tags",
            buildQuery(params),
            TAG_ROWS
        );

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object tag = row.get("tag");
            if (tag == null) {
                continue;
            }
            String value = tag.toString().trim();
            if (!value.isBlank()) {
                tags.add(value);
            }
        }
        return new ArrayList<>(tags);
    }

    private String escapeForIlike(String value) {
        return value.replace("%", "").replace("*", "");
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }
}

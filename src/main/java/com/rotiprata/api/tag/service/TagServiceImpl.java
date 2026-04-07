package com.rotiprata.api.tag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * Handles query building, normalization, and removal of duplicates.
 */
@Service
public class TagServiceImpl implements TagService {

    private static final TypeReference<List<Map<String, Object>>> TAG_ROWS = new TypeReference<>() {};
    private static final int DEFAULT_LIMIT = 20;

    private final SupabaseAdminRestClient adminRestClient;

    public TagServiceImpl(SupabaseAdminRestClient adminRestClient) {
        this.adminRestClient = adminRestClient;
    }

    /**
     * Searches for tags matching the provided query string.
     */
    @Override
    public List<String> searchTags(String query) {
        String normalized = query == null ? "" : query.trim();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", "tag");  // Only fetch the 'tag' column
        if (!normalized.isBlank()) {
            // Use case-insensitive partial match; sanitize input for ilike
            params.put("tag", "ilike.*" + escapeForIlike(normalized) + "*");
        }
        params.put("order", "tag.asc");  // Sort ascending
        params.put("limit", String.valueOf(DEFAULT_LIMIT));

        // Fetch rows from Supabase
        List<Map<String, Object>> rows = adminRestClient.getList(
                "content_tags",
                buildQuery(params),
                TAG_ROWS
        );

        // Collect unique, non-blank tags
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object tag = row.get("tag");
            if (tag == null) continue;
            String value = tag.toString().trim();
            if (!value.isBlank()) tags.add(value);
        }

        return new ArrayList<>(tags);
    }

    /** Escapes characters that could interfere with Supabase ilike queries. */
    private String escapeForIlike(String value) {
        return value.replace("%", "").replace("*", "");
    }

    /** Builds a query string from parameters for Supabase GET requests. */
    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }
}
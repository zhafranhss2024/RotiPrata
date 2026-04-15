package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for enriching content items with creator profile information.
 */
@Service
public class ContentCreatorEnrichmentServiceImpl implements ContentCreatorEnrichmentService {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseAdminRestClient;

    /**
     * Creates a content creator enrichment service impl instance with its collaborators.
     */
    public ContentCreatorEnrichmentServiceImpl(SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    /**
     * Handles enrich with creator profiles.
     */
    /** Enriches a list of content items with creator profiles. */
    @Override
    public List<Map<String, Object>> enrichWithCreatorProfiles(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return items;

        Set<UUID> creatorIds = extractCreatorIds(items);
        if (creatorIds.isEmpty()) return items;

        Map<UUID, Map<String, Object>> profiles = fetchProfilesByUserId(creatorIds);
        return attachProfilesToItems(items, profiles);
    }

    /**
     * Extracts the creator ids.
     */
    /** Extracts unique creator IDs from a list of items. */
    private Set<UUID> extractCreatorIds(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> parseUuid(item == null ? null : item.get("creator_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Fetches the profiles by user id.
     */
    /** Fetches profiles from Supabase for a given set of user IDs. */
    private Map<UUID, Map<String, Object>> fetchProfilesByUserId(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();

        String inClause = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
                "profiles",
                buildQuery(Map.of(
                        "select", "user_id,display_name,avatar_url",
                        "user_id", "in.(" + inClause + ")"
                )),
                MAP_LIST
        );

        return rows.stream()
                .map(this::mapProfile)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(profile -> (UUID) profile.get("user_id"), profile -> profile, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Attaches the profiles to items.
     */
    /** Attaches fetched profiles to the original items by creator_id. */
    private List<Map<String, Object>> attachProfilesToItems(List<Map<String, Object>> items, Map<UUID, Map<String, Object>> profiles) {
        for (Map<String, Object> item : items) {
            if (item == null) continue;
            UUID creatorId = parseUuid(item.get("creator_id"));
            if (creatorId != null && profiles.containsKey(creatorId)) {
                item.put("creator", profiles.get(creatorId));
            }
        }
        return items;
    }

    /**
     * Maps the profile.
     */
    /** Maps a single row from Supabase to a profile map. */
    private Map<String, Object> mapProfile(Map<String, Object> row) {
        UUID userId = parseUuid(row.get("user_id"));
        if (userId == null) return null;

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", userId);
        profile.put("display_name", normalizeNullableText(toStringOrNull(row.get("display_name"))));
        profile.put("avatar_url", normalizeNullableText(toStringOrNull(row.get("avatar_url"))));
        return profile;
    }

    /**
     * Parses the uuid.
     */
    /** Safely parses an object to UUID, returning null on failure. */
    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try { return UUID.fromString(value.toString()); }
        catch (RuntimeException ex) { return null; }
    }

    /**
     * Builds the query.
     */
    /** Builds a query string from parameters for Supabase GET requests. */
    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    /**
     * Converts the value into string or null.
     */
    /** Converts an object to a string or null if null. */
    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Normalizes the nullable text.
     */
    /** Normalizes nullable text: trims and converts empty strings to null. */
    private String normalizeNullableText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

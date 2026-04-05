package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RecommendationSignalService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationSignalService.class);
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int INTERACTION_SCAN_LIMIT = 40;
    private static final int SEARCH_TERM_LIMIT = 12;

    private final SupabaseAdminRestClient supabaseAdminRestClient;

    public RecommendationSignalService(SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    public RecommendationSignals loadSignals(UUID userId) {
        Set<UUID> likedContentIds = fetchContentIds("content_likes", "created_at", userId);
        Set<UUID> savedContentIds = fetchContentIds("content_saves", "created_at", userId);
        Set<UUID> sharedContentIds = fetchContentIds("content_shares", "shared_at", userId);
        Set<UUID> browsedContentIds = fetchBrowsingContentIds(userId);
        Set<UUID> masteredContentIds = fetchMasteredContentIds(userId);
        Map<UUID, RecommendationSignals.LessonProgressSignal> lessonProgressByLessonId = fetchLessonProgress(userId);
        List<String> recentSearchTerms = fetchRecentSearchTerms(userId);
        Map<UUID, Integer> recentImpressionCounts = fetchRecentImpressionCounts(userId);

        Set<UUID> interactionIds = new LinkedHashSet<>();
        interactionIds.addAll(likedContentIds);
        interactionIds.addAll(savedContentIds);
        interactionIds.addAll(sharedContentIds);
        interactionIds.addAll(browsedContentIds);

        Map<String, Integer> tagAffinity = new LinkedHashMap<>();
        Map<UUID, Integer> categoryAffinity = new LinkedHashMap<>();
        Map<UUID, Integer> creatorAffinity = new LinkedHashMap<>();
        if (!interactionIds.isEmpty()) {
            buildAffinityMaps(
                interactionIds,
                likedContentIds,
                savedContentIds,
                sharedContentIds,
                browsedContentIds,
                tagAffinity,
                categoryAffinity,
                creatorAffinity
            );
        }

        return new RecommendationSignals(
            lessonProgressByLessonId,
            likedContentIds,
            savedContentIds,
            sharedContentIds,
            browsedContentIds,
            masteredContentIds,
            tagAffinity,
            categoryAffinity,
            creatorAffinity,
            recentSearchTerms,
            recentImpressionCounts
        );
    }

    private Set<UUID> fetchContentIds(String table, String orderedByColumn, UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            table,
            buildQuery(Map.of(
                "select", "content_id",
                "user_id", "eq." + userId,
                "order", orderedByColumn + ".desc",
                "limit", String.valueOf(INTERACTION_SCAN_LIMIT)
            )),
            MAP_LIST
        );
        return rows.stream()
            .map(row -> parseUuid(row.get("content_id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> fetchBrowsingContentIds(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "browsing_history",
            buildQuery(Map.of(
                "select", "content_id",
                "user_id", "eq." + userId,
                "content_id", "not.is.null",
                "order", "viewed_at.desc",
                "limit", String.valueOf(INTERACTION_SCAN_LIMIT)
            )),
            MAP_LIST
        );
        return rows.stream()
            .map(row -> parseUuid(row.get("content_id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> fetchMasteredContentIds(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "user_concepts_mastered",
            buildQuery(Map.of(
                "select", "content_id",
                "user_id", "eq." + userId
            )),
            MAP_LIST
        );
        return rows.stream()
            .map(row -> parseUuid(row.get("content_id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<UUID, RecommendationSignals.LessonProgressSignal> fetchLessonProgress(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of(
                "select", "lesson_id,status,progress_percentage,last_accessed_at",
                "user_id", "eq." + userId
            )),
            MAP_LIST
        );

        Map<UUID, RecommendationSignals.LessonProgressSignal> progressByLessonId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID lessonId = parseUuid(row.get("lesson_id"));
            if (lessonId == null) {
                continue;
            }
            progressByLessonId.put(
                lessonId,
                new RecommendationSignals.LessonProgressSignal(
                    stringValue(row.get("status")),
                    parseInt(row.get("progress_percentage"), 0),
                    parseOffsetDateTime(row.get("last_accessed_at"))
                )
            );
        }
        return progressByLessonId;
    }

    private List<String> fetchRecentSearchTerms(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "search_history",
            buildQuery(Map.of(
                "select", "query",
                "user_id", "eq." + userId,
                "order", "searched_at.desc",
                "limit", String.valueOf(SEARCH_TERM_LIMIT)
            )),
            MAP_LIST
        );

        Set<String> terms = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            tokenize(stringValue(row.get("query"))).forEach(terms::add);
        }
        return new ArrayList<>(terms);
    }

    private Map<UUID, Integer> fetchRecentImpressionCounts(UUID userId) {
        try {
            List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
                "recommendation_impressions",
                buildQuery(Map.of(
                    "select", "content_id,created_at",
                    "user_id", "eq." + userId,
                    "order", "created_at.desc",
                    "limit", "120"
                )),
                MAP_LIST
            );

            OffsetDateTime stableBefore = OffsetDateTime.now().minusMinutes(10);
            Map<UUID, Integer> counts = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                UUID contentId = parseUuid(row.get("content_id"));
                OffsetDateTime createdAt = parseOffsetDateTime(row.get("created_at"));
                if (contentId == null || createdAt == null || !createdAt.isBefore(stableBefore)) {
                    continue;
                }
                counts.merge(contentId, 1, Integer::sum);
            }
            return counts;
        } catch (ResponseStatusException ex) {
            if (isMissingImpressionTable(ex)) {
                log.debug("recommendation_impressions table not available yet");
                return Map.of();
            }
            throw ex;
        }
    }

    private void buildAffinityMaps(
        Set<UUID> interactionIds,
        Set<UUID> likedContentIds,
        Set<UUID> savedContentIds,
        Set<UUID> sharedContentIds,
        Set<UUID> browsedContentIds,
        Map<String, Integer> tagAffinity,
        Map<UUID, Integer> categoryAffinity,
        Map<UUID, Integer> creatorAffinity
    ) {
        List<Map<String, Object>> contents = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,category_id,creator_id",
                "id", "in.(" + joinUuids(interactionIds) + ")"
            )),
            MAP_LIST
        );

        Map<UUID, Integer> weightByContentId = new LinkedHashMap<>();
        interactionIds.forEach(contentId -> weightByContentId.put(contentId, interactionWeight(
            contentId,
            likedContentIds,
            savedContentIds,
            sharedContentIds,
            browsedContentIds
        )));

        for (Map<String, Object> content : contents) {
            UUID contentId = parseUuid(content.get("id"));
            if (contentId == null) {
                continue;
            }
            int weight = weightByContentId.getOrDefault(contentId, 1);
            UUID categoryId = parseUuid(content.get("category_id"));
            UUID creatorId = parseUuid(content.get("creator_id"));
            if (categoryId != null) {
                categoryAffinity.merge(categoryId, weight, Integer::sum);
            }
            if (creatorId != null) {
                creatorAffinity.merge(creatorId, weight, Integer::sum);
            }
        }

        List<Map<String, Object>> tags = supabaseAdminRestClient.getList(
            "content_tags",
            buildQuery(Map.of(
                "select", "content_id,tag",
                "content_id", "in.(" + joinUuids(interactionIds) + ")"
            )),
            MAP_LIST
        );

        for (Map<String, Object> tagRow : tags) {
            UUID contentId = parseUuid(tagRow.get("content_id"));
            String tag = normalizeTag(tagRow.get("tag"));
            if (contentId == null || tag == null) {
                continue;
            }
            tagAffinity.merge(tag, weightByContentId.getOrDefault(contentId, 1), Integer::sum);
        }
    }

    private int interactionWeight(
        UUID contentId,
        Set<UUID> likedContentIds,
        Set<UUID> savedContentIds,
        Set<UUID> sharedContentIds,
        Set<UUID> browsedContentIds
    ) {
        int weight = 0;
        if (likedContentIds.contains(contentId)) {
            weight += 3;
        }
        if (savedContentIds.contains(contentId)) {
            weight += 4;
        }
        if (sharedContentIds.contains(contentId)) {
            weight += 5;
        }
        if (browsedContentIds.contains(contentId)) {
            weight += 1;
        }
        return Math.max(weight, 1);
    }

    private List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        return List.of(normalized.split("\\s+")).stream()
            .map(String::trim)
            .filter(token -> token.length() >= 3)
            .limit(8)
            .toList();
    }

    private boolean isMissingImpressionTable(ResponseStatusException ex) {
        String reason = ex.getReason();
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("recommendation_impressions")
            || normalized.contains("pgrst205")
            || normalized.contains("does not exist");
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String joinUuids(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
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

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private OffsetDateTime parseOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeTag(Object value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}

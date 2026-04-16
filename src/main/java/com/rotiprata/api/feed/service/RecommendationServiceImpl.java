package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentEngagementService;
import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkedLesson;
import com.rotiprata.api.feed.service.RecommendationScorer.ScoredRecommendation;
import com.rotiprata.api.feed.response.FeedResponse;
import com.rotiprata.api.feed.response.RecommendationResponse;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Implements the recommendation service workflows and persistence coordination used by the API layer.
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int DEFAULT_FEED_LIMIT = 20;
    private static final int MAX_FEED_LIMIT = 50;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 24;
    private static final int MAX_RECOMMENDATION_LIMIT = 48;
    private static final int RECENT_CANDIDATE_LIMIT = 160;
    private static final int POPULAR_CANDIDATE_LIMIT = 80;
    private static final String CONTENT_SELECT = String.join(
        ",",
        "id",
        "creator_id",
        "title",
        "description",
        "content_type",
        "media_url",
        "thumbnail_url",
        "category_id",
        "status",
        "learning_objective",
        "origin_explanation",
        "definition_literal",
        "definition_used",
        "older_version_reference",
        "educational_value_votes",
        "view_count",
        "is_featured",
        "reviewed_by",
        "reviewed_at",
        "review_feedback",
        "created_at",
        "updated_at",
        "is_submitted",
        "media_status",
        "likes_count",
        "comments_count",
        "saves_count",
        "shares_count"
    );

    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final ContentEngagementService contentEngagementService;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;
    private final RecommendationSignalService recommendationSignalService;
    private final ContentLessonLinkService contentLessonLinkService;
    private final RecommendationScorer recommendationScorer;

    /**
     * Creates a recommendation service instance with its collaborators.
     */
    public RecommendationServiceImpl(
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentEngagementService contentEngagementService,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService,
        RecommendationSignalService recommendationSignalService,
        ContentLessonLinkService contentLessonLinkService,
        RecommendationScorer recommendationScorer
    ) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.contentEngagementService = contentEngagementService;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
        this.recommendationSignalService = recommendationSignalService;
        this.contentLessonLinkService = contentLessonLinkService;
        this.recommendationScorer = recommendationScorer;
    }

    /**
     * Returns the feed.
     */
    public FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        int boundedLimit = normalizeFeedLimit(limit);
        RecommendationCursor cursorKey = decodeCursor(cursor);

        List<ScoredRecommendation> ranked = rankCandidates(userId, RecommendationSurface.FEED);
        List<ScoredRecommendation> pageCandidates = applyCursor(ranked, cursorKey).stream()
            .limit(boundedLimit + 1L)
            .toList();

        boolean hasMore = pageCandidates.size() > boundedLimit;
        List<ScoredRecommendation> page = hasMore ? pageCandidates.subList(0, boundedLimit) : pageCandidates;
        List<Map<String, Object>> hydrated = hydrate(page, userId, token);
        logImpressions(userId, RecommendationSurface.FEED, page);

        String nextCursor = hasMore && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1)) : null;
        return new FeedResponse(hydrated, hasMore && nextCursor != null, nextCursor);
    }

    /**
     * Returns the recommendations.
     */
    public RecommendationResponse getRecommendations(UUID userId, String accessToken, Integer limit) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        int boundedLimit = normalizeRecommendationLimit(limit);

        List<ScoredRecommendation> ranked = rankCandidates(userId, RecommendationSurface.EXPLORE).stream()
            .limit(boundedLimit)
            .toList();
        List<Map<String, Object>> hydrated = hydrate(ranked, userId, token);
        logImpressions(userId, RecommendationSurface.EXPLORE, ranked);
        return new RecommendationResponse(hydrated);
    }

    /**
     * Handles rank candidates.
     */
    private List<ScoredRecommendation> rankCandidates(UUID userId, RecommendationSurface surface) {
        List<Map<String, Object>> candidatePool = fetchCandidatePool();
        if (candidatePool.isEmpty()) {
            return List.of();
        }

        attachTags(candidatePool);
        Set<UUID> contentIds = candidatePool.stream()
            .map(row -> parseUuid(row.get("id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        RecommendationSignals signals = recommendationSignalService.loadSignals(userId);
        Map<UUID, List<LinkedLesson>> linkedLessons = contentLessonLinkService.resolveLinkedLessons(contentIds);

        return candidatePool.stream()
            .map(item -> recommendationScorer.score(
                item,
                linkedLessons.getOrDefault(parseUuid(item.get("id")), List.of()),
                signals,
                surface
            ))
            .sorted(recommendationScorer.comparator())
            .toList();
    }

    /**
     * Handles hydrate.
     */
    private List<Map<String, Object>> hydrate(List<ScoredRecommendation> scored, UUID userId, String accessToken) {
        if (scored.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ScoredRecommendation recommendation : scored) {
            Map<String, Object> copy = new LinkedHashMap<>(recommendation.item());
            copy.put("recommendation_score", recommendation.score());
            items.add(copy);
        }

        List<Map<String, Object>> decorated = contentEngagementService.decorateItemsWithUserEngagement(items, userId, accessToken);
        List<Map<String, Object>> enriched = contentCreatorEnrichmentService.enrichWithCreatorProfiles(decorated);
        enriched.forEach(this::attachStreamFields);
        return enriched;
    }

    /**
     * Applies the cursor.
     */
    private List<ScoredRecommendation> applyCursor(List<ScoredRecommendation> ranked, RecommendationCursor cursorKey) {
        if (cursorKey == null) {
            return ranked;
        }
        return ranked.stream()
            .filter(item -> compareToCursor(item, cursorKey) > 0)
            .toList();
    }

    /**
     * Handles compare to cursor.
     */
    private int compareToCursor(ScoredRecommendation item, RecommendationCursor cursorKey) {
        int scoreCompare = Double.compare(cursorKey.score(), item.score());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        OffsetDateTime createdAt = item.createdAt();
        if (cursorKey.createdAt() != null && createdAt != null) {
            int createdAtCompare = cursorKey.createdAt().compareTo(createdAt);
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
        } else if (cursorKey.createdAt() != null) {
            return -1;
        } else if (createdAt != null) {
            return 1;
        }
        if (cursorKey.contentId() == null || item.contentId() == null) {
            return 0;
        }
        return cursorKey.contentId().compareTo(item.contentId());
    }

    /**
     * Fetches the candidate pool.
     */
    private List<Map<String, Object>> fetchCandidatePool() {
        Map<String, String> recentParams = baseCandidateParams();
        recentParams.put("order", "created_at.desc,id.desc");
        recentParams.put("limit", String.valueOf(RECENT_CANDIDATE_LIMIT));

        Map<String, String> popularParams = baseCandidateParams();
        popularParams.put("order", "likes_count.desc,saves_count.desc,shares_count.desc,view_count.desc,created_at.desc,id.desc");
        popularParams.put("limit", String.valueOf(POPULAR_CANDIDATE_LIMIT));

        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(fetchContentRowsWithMediaFallback(recentParams));
        merged.addAll(fetchContentRowsWithMediaFallback(popularParams));

        Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> row : merged) {
            String id = stringValue(row.get("id"));
            if (id != null) {
                deduped.putIfAbsent(id, row);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * Handles base candidate params.
     */
    private Map<String, String> baseCandidateParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", CONTENT_SELECT);
        params.put("status", "eq.approved");
        params.put("is_submitted", "eq.true");
        params.put("content_type", "eq.video");
        params.put("media_status", "eq.ready");
        params.put("media_url", "not.is.null");
        return params;
    }

    /**
     * Fetches the content rows with media fallback.
     */
    private List<Map<String, Object>> fetchContentRowsWithMediaFallback(Map<String, String> params) {
        try {
            return supabaseAdminRestClient.getList("content", buildQuery(params), MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!shouldRetryWithoutMediaStatus(ex)) {
                throw ex;
            }
            Map<String, String> fallback = new LinkedHashMap<>(params);
            fallback.remove("media_status");
            return supabaseAdminRestClient.getList("content", buildQuery(fallback), MAP_LIST);
        }
    }

    /**
     * Attaches the tags.
     */
    private void attachTags(List<Map<String, Object>> items) {
        Set<UUID> contentIds = items.stream()
            .map(item -> parseUuid(item.get("id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (contentIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> tagRows = supabaseAdminRestClient.getList(
            "content_tags",
            buildQuery(Map.of(
                "select", "content_id,tag",
                "content_id", "in.(" + contentIds.stream().map(UUID::toString).collect(Collectors.joining(",")) + ")"
            )),
            MAP_LIST
        );

        Map<String, List<String>> tagsByContentId = new LinkedHashMap<>();
        for (Map<String, Object> row : tagRows) {
            String contentId = stringValue(row.get("content_id"));
            String tag = normalizeTag(row.get("tag"));
            if (contentId == null || tag == null) {
                continue;
            }
            tagsByContentId.computeIfAbsent(contentId, ignored -> new ArrayList<>()).add(tag);
        }

        for (Map<String, Object> item : items) {
            item.put("tags", tagsByContentId.getOrDefault(stringValue(item.get("id")), List.of()));
        }
    }

    /**
     * Handles log impressions.
     */
    private void logImpressions(UUID userId, RecommendationSurface surface, List<ScoredRecommendation> ranked) {
        if (ranked.isEmpty()) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            ScoredRecommendation recommendation = ranked.get(index);
            if (recommendation.contentId() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", userId);
            row.put("content_id", recommendation.contentId());
            row.put("surface", surface.name().toLowerCase(Locale.ROOT));
            row.put("rank_position", index + 1);
            row.put("request_id", requestId);
            row.put("score", recommendation.score());
            row.put("created_at", now);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return;
        }

        /*
         * Impressions are persisted best-effort. The table can be deployed after the
         * application code, so recommendation serving must not fail while rollout catches up.
         */
        try {
            supabaseAdminRestClient.postList("recommendation_impressions", rows, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (shouldIgnoreMissingImpressionsTable(ex)) {
                log.debug("Skipping recommendation impression logging until schema is deployed");
                return;
            }
            throw ex;
        }
    }

    /**
     * Attaches the stream fields.
     */
    private void attachStreamFields(Map<String, Object> item) {
        if (item == null) {
            return;
        }
        String mediaUrl = stringValue(item.get("media_url"));
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return;
        }
        item.put("stream_url", mediaUrl);
        String lower = mediaUrl.toLowerCase(Locale.ROOT);
        item.put("stream_type", lower.contains(".m3u8") ? "hls" : "file");
    }

    /**
     * Requires the access token.
     */
    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    /**
     * Normalizes the feed limit.
     */
    private int normalizeFeedLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_FEED_LIMIT;
        }
        return Math.min(MAX_FEED_LIMIT, limit);
    }

    /**
     * Normalizes the recommendation limit.
     */
    private int normalizeRecommendationLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_RECOMMENDATION_LIMIT;
        }
        return Math.min(MAX_RECOMMENDATION_LIMIT, limit);
    }

    /**
     * Handles encode cursor.
     */
    private String encodeCursor(ScoredRecommendation item) {
        String payload = item.score() + "|" + item.createdAt() + "|" + item.contentId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Handles decode cursor.
     */
    private RecommendationCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid cursor");
            }
            return new RecommendationCursor(
                Double.parseDouble(parts[0]),
                OffsetDateTime.parse(parts[1]),
                UUID.fromString(parts[2])
            );
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    /**
     * Handles should retry without media status.
     */
    private boolean shouldRetryWithoutMediaStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("media_status") || normalized.contains("pgrst204");
    }

    /**
     * Handles should ignore missing impressions table.
     */
    private boolean shouldIgnoreMissingImpressionsTable(ResponseStatusException ex) {
        String reason = ex.getReason();
        if (reason == null) {
            return false;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("recommendation_impressions")
            || normalized.contains("pgrst205")
            || normalized.contains("does not exist");
    }

    /**
     * Builds the query.
     */
    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    /**
     * Parses the uuid.
     */
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

    /**
     * Extracts a string value from a mixed payload field.
     */
    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Normalizes the tag.
     */
    private String normalizeTag(Object value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private record RecommendationCursor(double score, OffsetDateTime createdAt, UUID contentId) {}
}

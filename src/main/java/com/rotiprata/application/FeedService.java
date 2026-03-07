package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.FeedResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FeedService {
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String FEED_SELECT = String.join(
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

    private final SupabaseRestClient supabaseRestClient;
    private final ContentEngagementService contentEngagementService;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;

    public FeedService(
        SupabaseRestClient supabaseRestClient,
        ContentEngagementService contentEngagementService,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.contentEngagementService = contentEngagementService;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
    }

    public FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit) {
        String token = requireAccessToken(accessToken);
        int boundedLimit = normalizeLimit(limit);
        CursorKey cursorKey = decodeCursor(cursor);
        long startedAt = System.currentTimeMillis();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", FEED_SELECT);
        params.put("status", "eq.approved");
        params.put("is_submitted", "eq.true");
        params.put("content_type", "eq.video");
        params.put("media_status", "eq.ready");
        params.put("media_url", "not.is.null");
        params.put("order", "created_at.desc,id.desc");
        params.put("limit", String.valueOf(boundedLimit + 1));
        if (cursorKey != null) {
            params.put(
                "or",
                "(created_at.lt." + cursorKey.createdAt().toInstant() + ",and(created_at.eq."
                    + cursorKey.createdAt().toInstant() + ",id.lt." + cursorKey.id() + "))"
            );
        }

        List<Map<String, Object>> rows = fetchFeedRowsWithFallback(params, token);

        boolean hasMore = rows.size() > boundedLimit;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, boundedLimit) : rows;
        List<Map<String, Object>> decorated = contentEngagementService.decorateItemsWithUserEngagement(
            new java.util.ArrayList<>(items),
            userId,
            token
        );
        List<Map<String, Object>> enriched = contentCreatorEnrichmentService.enrichWithCreatorProfiles(decorated);
        enriched.forEach(this::attachStreamFields);

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Map<String, Object> last = items.get(items.size() - 1);
            OffsetDateTime createdAt = parseOffsetDateTime(last.get("created_at"));
            UUID id = parseUuid(last.get("id"));
            if (createdAt != null && id != null) {
                nextCursor = encodeCursor(createdAt, id);
            }
        }
        if (hasMore && nextCursor == null) {
            hasMore = false;
        }

        log.debug(
            "Feed fetched limit={} returned={} hasMore={} durationMs={}",
            boundedLimit,
            items.size(),
            hasMore,
            System.currentTimeMillis() - startedAt
        );
        return new FeedResponse(enriched, hasMore, nextCursor);
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, limit);
    }

    private String encodeCursor(OffsetDateTime createdAt, UUID id) {
        String payload = createdAt.toInstant() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private CursorKey decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            OffsetDateTime createdAt = OffsetDateTime.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new CursorKey(createdAt, id);
        } catch (RuntimeException ex) {
            log.warn("Failed to decode feed cursor");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private OffsetDateTime parseOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
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

    private void attachStreamFields(Map<String, Object> item) {
        if (item == null) {
            return;
        }
        Object mediaUrlValue = item.get("media_url");
        if (mediaUrlValue == null) {
            return;
        }
        String mediaUrl = mediaUrlValue.toString();
        if (mediaUrl.isBlank()) {
            return;
        }
        item.put("stream_url", mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) {
            item.put("stream_type", "hls");
            return;
        }
        item.put("stream_type", "file");
    }

    private List<Map<String, Object>> fetchFeedRowsWithFallback(Map<String, String> params, String token) {
        try {
            return supabaseRestClient.getList(
                "content",
                buildQuery(params),
                token,
                MAP_LIST
            );
        } catch (ResponseStatusException ex) {
            if (!shouldRetryWithoutMediaStatus(ex)) {
                throw ex;
            }
            Map<String, String> fallbackParams = new LinkedHashMap<>(params);
            fallbackParams.remove("media_status");
            return supabaseRestClient.getList(
                "content",
                buildQuery(fallbackParams),
                token,
                MAP_LIST
            );
        }
    }

    private boolean shouldRetryWithoutMediaStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        String message = ex.getMessage();
        if (reason != null) {
            String lower = reason.toLowerCase();
            if (lower.contains("media_status") || lower.contains("pgrst204")) {
                return true;
            }
        }
        if (message != null) {
            String lower = message.toLowerCase();
            return lower.contains("media_status") || lower.contains("pgrst204");
        }
        return false;
    }

    private record CursorKey(OffsetDateTime createdAt, UUID id) {}
}

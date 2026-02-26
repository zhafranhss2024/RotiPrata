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
        params.put("select", "*");
        params.put("status", "eq.approved");
        params.put("order", "created_at.desc,id.desc");
        params.put("limit", String.valueOf(boundedLimit + 1));
        if (cursorKey != null) {
            params.put(
                "or",
                "(created_at.lt." + cursorKey.createdAt().toInstant() + ",and(created_at.eq."
                    + cursorKey.createdAt().toInstant() + ",id.lt." + cursorKey.id() + "))"
            );
        }

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content",
            buildQuery(params),
            token,
            MAP_LIST
        );

        boolean hasMore = rows.size() > boundedLimit;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, boundedLimit) : rows;
        List<Map<String, Object>> decorated = contentEngagementService.decorateItemsWithUserEngagement(
            new java.util.ArrayList<>(items),
            userId,
            token
        );
        List<Map<String, Object>> enriched = contentCreatorEnrichmentService.enrichWithCreatorProfiles(decorated);

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

    private record CursorKey(OffsetDateTime createdAt, UUID id) {}
}

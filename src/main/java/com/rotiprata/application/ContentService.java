package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentCommentCreateRequest;
import com.rotiprata.api.dto.ContentCommentResponse;
import com.rotiprata.api.dto.ContentFlagRequest;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentService {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CONTENT_TYPE = "content_type";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final ContentEngagementService contentEngagementService;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;

    public ContentService(
        SupabaseRestClient supabaseRestClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentEngagementService contentEngagementService,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.contentEngagementService = contentEngagementService;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
    }

    public Map<String, Object> getContentById(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "*",
                "id", "eq." + contentId,
                "status", "eq.approved",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (rows.isEmpty()) {
            rows = supabaseAdminRestClient.getList(
                "content",
                buildQuery(Map.of(
                    "select", "*",
                    "id", "eq." + contentId,
                    "creator_id", "eq." + userId,
                    "limit", "1"
                )),
                MAP_LIST
            );
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
        List<Map<String, Object>> decorated = contentEngagementService.decorateItemsWithUserEngagement(rows, userId, token);
        List<Map<String, Object>> enriched = contentCreatorEnrichmentService.enrichWithCreatorProfiles(decorated);
        Map<String, Object> item = enriched.get(0);
        item.put("tags", fetchTagsForContent(contentId));
        return item;
    }

    private List<String> fetchTagsForContent(UUID contentId) {
        if (contentId == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content_tags",
            buildQuery(Map.of(
                "select", "tag",
                "content_id", "eq." + contentId
            )),
            MAP_LIST
        );
        List<String> tags = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object tag = row.get("tag");
            if (tag != null) {
                String value = tag.toString().trim();
                if (!value.isBlank()) {
                    tags.add(value);
                }
            }
        }
        return tags;
    }

    public List<ContentSearchDTO> getFilteredContent(String query, String filter, String accessToken) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String path = "/content";
        String trimmedQuery = query.trim();
        String safeQuery = escapeQuery(trimmedQuery);
        String selectColumns = String.join(",", ID, TITLE, DESCRIPTION, CONTENT_TYPE);
        String filterQuery = String.format(
            "select=%s&status=eq.approved&is_submitted=eq.true&content_type=eq.video&or=(title.ilike.*%s*,description.ilike.*%s*)&limit=10",
            selectColumns,
            safeQuery,
            safeQuery
        );

        if (filter != null && !filter.isBlank() && "video".equalsIgnoreCase(filter)) {
            // already enforced as video
        }

        List<ContentSearchDTO> titleResults = supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<ContentSearchDTO>>() {}
        );

        Map<String, ContentSearchDTO> deduped = new LinkedHashMap<>();
        for (ContentSearchDTO dto : titleResults) {
            deduped.put(dto.id(), withSnippet(dto));
        }

        List<String> tagIds = fetchContentIdsByTag(safeQuery, accessToken);
        if (!tagIds.isEmpty()) {
            List<ContentSearchDTO> tagResults = fetchContentByIds(tagIds, accessToken);
            for (ContentSearchDTO dto : tagResults) {
                deduped.putIfAbsent(dto.id(), withSnippet(dto));
            }
        }

        return new ArrayList<>(deduped.values());
    }

    public void trackView(UUID userId, UUID contentId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        ensureContentExists(contentId);

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,view_count",
                "id", "eq." + contentId
            )),
            MAP_LIST
        );

        Map<String, Object> row = rows.get(0);
        int currentCount = toInt(row.get("view_count"));
        int nextCount = Math.max(0, currentCount + 1);

        supabaseAdminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            Map.of(
                "view_count", nextCount,
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );
    }

    public void likeContent(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        if (existsByContentAndUser("content_likes", contentId, userId, token)) {
            return;
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("user_id", userId);
        insert.put("created_at", OffsetDateTime.now());

        try {
            supabaseRestClient.postList("content_likes", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
        }

        refreshEngagementCounts(contentId);
    }

    public void unlikeContent(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        supabaseRestClient.deleteList(
            "content_likes",
            buildQuery(Map.of(
                "content_id", "eq." + contentId,
                "user_id", "eq." + userId
            )),
            token,
            MAP_LIST
        );

        refreshEngagementCounts(contentId);
    }

    public void saveContent(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        if (existsByContentAndUser("content_saves", contentId, userId, token)) {
            return;
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("user_id", userId);
        insert.put("created_at", OffsetDateTime.now());

        try {
            supabaseRestClient.postList("content_saves", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
        }

        refreshEngagementCounts(contentId);
    }

    public void unsaveContent(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        supabaseRestClient.deleteList(
            "content_saves",
            buildQuery(Map.of(
                "content_id", "eq." + contentId,
                "user_id", "eq." + userId
            )),
            token,
            MAP_LIST
        );

        refreshEngagementCounts(contentId);
    }

    public void shareContent(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("user_id", userId);
        insert.put("shared_at", OffsetDateTime.now());

        try {
            supabaseRestClient.postList("content_shares", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
        }
        refreshEngagementCounts(contentId);
    }

    public void flagContent(UUID userId, UUID contentId, ContentFlagRequest request, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("reported_by", userId);
        insert.put("reason", request.reason());
        insert.put("description", normalizeNullableText(request.description()));
        insert.put("created_at", OffsetDateTime.now());

        try {
            supabaseRestClient.postList("content_flags", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (isUniqueViolation(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You already flagged this content");
            }
            throw ex;
        }
    }

    public List<ContentCommentResponse> listComments(
        UUID userId,
        UUID contentId,
        int limit,
        int offset,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        int boundedLimit = Math.max(1, Math.min(limit, 200));
        int boundedOffset = Math.max(0, offset);

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content_comments",
            buildQuery(Map.of(
                "select", "id,content_id,user_id,parent_id,body,created_at,updated_at",
                "content_id", "eq." + contentId,
                "is_deleted", "eq.false",
                "order", "created_at.asc",
                "limit", String.valueOf(boundedLimit),
                "offset", String.valueOf(boundedOffset)
            )),
            token,
            MAP_LIST
        );

        Set<UUID> userIds = rows.stream()
            .map(row -> parseUuid(row.get("user_id")))
            .filter(uuid -> uuid != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, String> authors = fetchDisplayNames(userIds);
        List<ContentCommentResponse> comments = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            comments.add(toCommentResponse(row, authors));
        }
        return comments;
    }

    public ContentCommentResponse createComment(
        UUID userId,
        UUID contentId,
        ContentCommentCreateRequest request,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);

        UUID parentId = request.parentId();
        if (parentId != null && !existsParentComment(contentId, parentId, token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent comment does not exist");
        }

        String body = normalizeNullableText(request.body());
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment body is required");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("user_id", userId);
        insert.put("parent_id", parentId);
        insert.put("body", body);
        insert.put("created_at", now);
        insert.put("updated_at", now);

        List<Map<String, Object>> created = supabaseRestClient.postList("content_comments", insert, token, MAP_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create comment");
        }

        refreshEngagementCounts(contentId);

        Map<String, Object> row = created.get(0);
        Map<UUID, String> authors = fetchDisplayNames(Set.of(userId));
        return toCommentResponse(row, authors);
    }

    private void refreshEngagementCounts(UUID contentId) {
        int likes = countByContent("content_likes", contentId, null);
        int saves = countByContent("content_saves", contentId, null);
        int shares = countByContent("content_shares", contentId, null);
        int comments = countByContent("content_comments", contentId, "is_deleted=eq.false");

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("likes_count", likes);
        patch.put("educational_value_votes", likes);
        patch.put("saves_count", saves);
        patch.put("shares_count", shares);
        patch.put("comments_count", comments);
        patch.put("updated_at", OffsetDateTime.now());

        supabaseAdminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            patch,
            MAP_LIST
        );
    }

    private int countByContent(String table, UUID contentId, String optionalCondition) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", "id");
        params.put("content_id", "eq." + contentId);

        if (optionalCondition != null && !optionalCondition.isBlank()) {
            String[] parts = optionalCondition.split("=", 2);
            if (parts.length == 2) {
                params.put(parts[0], parts[1]);
            }
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(table, buildQuery(params), MAP_LIST);
        return rows.size();
    }

    private ContentCommentResponse toCommentResponse(Map<String, Object> row, Map<UUID, String> authors) {
        UUID userId = parseUuid(row.get("user_id"));
        String author = authors.getOrDefault(userId, "anonymous");
        return new ContentCommentResponse(
            parseUuid(row.get("id")),
            parseUuid(row.get("content_id")),
            userId,
            parseUuid(row.get("parent_id")),
            toStringOrNull(row.get("body")),
            author,
            parseOffsetDateTime(row.get("created_at")),
            parseOffsetDateTime(row.get("updated_at"))
        );
    }

    private Map<UUID, String> fetchDisplayNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        String inClause = userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<Map<String, Object>> profileRows = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of(
                "select", "user_id,display_name",
                "user_id", "in.(" + inClause + ")"
            )),
            MAP_LIST
        );

        Map<UUID, String> authors = new HashMap<>();
        for (Map<String, Object> profile : profileRows) {
            UUID profileUserId = parseUuid(profile.get("user_id"));
            if (profileUserId == null) {
                continue;
            }
            String displayName = normalizeNullableText(toStringOrNull(profile.get("display_name")));
            if (displayName == null || displayName.isBlank()) {
                displayName = "anonymous";
            }
            authors.put(profileUserId, displayName);
        }

        return authors;
    }

    private boolean existsByContentAndUser(String table, UUID contentId, UUID userId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            table,
            buildQuery(Map.of(
                "select", "id",
                "content_id", "eq." + contentId,
                "user_id", "eq." + userId,
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return !rows.isEmpty();
    }

    private boolean existsParentComment(UUID contentId, UUID parentId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content_comments",
            buildQuery(Map.of(
                "select", "id",
                "id", "eq." + parentId,
                "content_id", "eq." + contentId,
                "is_deleted", "eq.false",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return !rows.isEmpty();
    }

    private void ensureUserAndContent(UUID userId, UUID contentId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        ensureContentExists(contentId);
    }

    private void ensureContentExists(UUID contentId) {
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id",
                "id", "eq." + contentId,
                "limit", "1"
            )),
            MAP_LIST
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
    }

    private List<String> fetchContentIdsByTag(String query, String accessToken) {
        String path = "/content_tags";
        String filterQuery = String.format("select=content_id&tag=ilike.*%s*&limit=20", query);
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("content_id");
            if (value != null) {
                ids.add(value.toString());
            }
        }
        return new ArrayList<>(ids);
    }

    private List<ContentSearchDTO> fetchContentByIds(List<String> ids, String accessToken) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String path = "/content";
        String selectColumns = String.join(",", ID, TITLE, DESCRIPTION, CONTENT_TYPE);
        String idList = String.join(",", ids);
        String filterQuery = String.format(
            "select=%s&status=eq.approved&is_submitted=eq.true&content_type=eq.video&id=in.(%s)&limit=10",
            selectColumns,
            idList
        );
        return supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<ContentSearchDTO>>() {}
        );
    }

    private ContentSearchDTO withSnippet(ContentSearchDTO c) {
        String desc = c.description();
        String snippet = (desc != null && desc.length() > 100)
            ? desc.substring(0, 100) + "..."
            : desc;
        return new ContentSearchDTO(
            c.id(),
            c.content_type(),
            c.title(),
            c.description(),
            snippet
        );
    }

    private boolean isUniqueViolation(ResponseStatusException ex) {
        StringBuilder normalizedBuilder = new StringBuilder();
        if (ex.getReason() != null) {
            normalizedBuilder.append(ex.getReason().toLowerCase());
        }
        if (ex.getCause() instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (body != null) {
                normalizedBuilder.append(" ").append(body.toLowerCase());
            }
        }
        String normalized = normalizedBuilder.toString();
        return normalized.contains("duplicate key")
            || normalized.contains("already exists")
            || normalized.contains("unique constraint")
            || normalized.contains("on conflict");
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

    private String escapeQuery(String query) {
        return query.replace(",", " ").replace("(", " ").replace(")", " ");
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }
}

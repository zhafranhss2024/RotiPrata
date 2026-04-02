package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentCommentCreateRequest;
import com.rotiprata.api.dto.ContentCommentResponse;
import com.rotiprata.api.dto.ContentFlagRequest;
import com.rotiprata.api.dto.ContentPlaybackEventRequest;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentService {
    private static final Logger log = LoggerFactory.getLogger(ContentService.class);
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
    private static final int DEFAULT_SIMILAR_LIMIT = 6;
    private static final int MAX_SIMILAR_LIMIT = 6;
    private static final int TAG_MATCH_SCAN_LIMIT = 250;

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CONTENT_TYPE = "content_type";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final ContentEngagementService contentEngagementService;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;
    private final UserService userService;

    public ContentService(
        SupabaseRestClient supabaseRestClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentEngagementService contentEngagementService,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService,
        UserService userService
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.contentEngagementService = contentEngagementService;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
        this.userService = userService;
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
        attachStreamFields(item);
        return item;
    }

    public List<Map<String, Object>> getSimilarContent(UUID userId, UUID contentId, String accessToken, Integer limit) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        Map<String, Object> currentContent = getContentById(userId, contentId, token);
        int boundedLimit = normalizeSimilarLimit(limit);
        List<String> currentTags = normalizeTags(currentContent.get("tags"));

        List<Map<String, Object>> selectedRows = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();

        if (!currentTags.isEmpty()) {
            Map<String, Integer> sharedTagCounts = fetchSharedTagCounts(contentId, currentTags);
            if (!sharedTagCounts.isEmpty()) {
                List<Map<String, Object>> matchedRows = fetchContentRowsByIds(sharedTagCounts.keySet(), token);
                matchedRows.sort(buildSimilarContentComparator(sharedTagCounts));
                for (Map<String, Object> row : matchedRows) {
                    if (selectedRows.size() >= boundedLimit) {
                        break;
                    }
                    String rowId = toStringOrNull(row.get("id"));
                    if (rowId == null || !selectedIds.add(rowId)) {
                        continue;
                    }
                    selectedRows.add(row);
                }
            }
        }

        return hydrateContentItems(selectedRows, userId, token);
    }

    public List<Map<String, Object>> getProfileContentCollection(UUID userId, String accessToken, String collection) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        String normalized = normalizeNullableText(collection);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collection is required");
        }
        return switch (normalized.toLowerCase()) {
            case "posted" -> getPostedProfileContent(userId, token);
            case "saved" -> getInteractionVideoCollection(userId, token, "content_saves");
            case "liked" -> getInteractionVideoCollection(userId, token, "content_likes");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profile content collection");
        };
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

    private Map<String, Integer> fetchSharedTagCounts(UUID currentContentId, List<String> tags) {
        Set<String> currentTagSet = tags.stream()
            .map(this::normalizeNullableText)
            .filter(tag -> tag != null && !tag.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currentTagSet.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> uniqueTagsByContentId = new LinkedHashMap<>();
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content_tags",
            buildQuery(Map.of(
                "select", "content_id,tag",
                "content_id", "not.eq." + currentContentId,
                "order", "created_at.desc",
                "limit", String.valueOf(TAG_MATCH_SCAN_LIMIT)
            )),
            MAP_LIST
        );
        for (Map<String, Object> row : rows) {
            String contentId = toStringOrNull(row.get("content_id"));
            String matchedTag = normalizeNullableText(toStringOrNull(row.get("tag")));
            if (contentId == null || matchedTag == null || !currentTagSet.contains(matchedTag)) {
                continue;
            }
            uniqueTagsByContentId.computeIfAbsent(contentId, ignored -> new LinkedHashSet<>()).add(matchedTag);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : uniqueTagsByContentId.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private List<Map<String, Object>> fetchContentRowsByIds(Set<String> ids, String accessToken) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", CONTENT_SELECT);
        params.put("id", "in.(" + String.join(",", ids) + ")");
        params.put("status", "eq.approved");
        params.put("is_submitted", "eq.true");
        params.put("content_type", "eq.video");
        params.put("media_url", "not.is.null");
        params.put("limit", String.valueOf(ids.size()));

        return fetchPlayableVideoRowsWithFallback(params, accessToken);
    }

    private List<Map<String, Object>> getPostedProfileContent(UUID userId, String accessToken) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", CONTENT_SELECT,
                "creator_id", "eq." + userId,
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
        return hydrateContentItems(rows, userId, accessToken);
    }

    private List<Map<String, Object>> getInteractionVideoCollection(UUID userId, String accessToken, String table) {
        List<Map<String, Object>> interactionRows = supabaseAdminRestClient.getList(
            table,
            buildQuery(Map.of(
                "select", "content_id,created_at",
                "user_id", "eq." + userId,
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
        if (interactionRows.isEmpty()) {
            return List.of();
        }

        List<String> orderedIds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : interactionRows) {
            String contentId = toStringOrNull(row.get("content_id"));
            if (contentId != null && seen.add(contentId)) {
                orderedIds.add(contentId);
            }
        }
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> contentRows = fetchContentRowsByIds(new LinkedHashSet<>(orderedIds), accessToken);
        List<Map<String, Object>> hydrated = hydrateContentItems(contentRows, userId, accessToken);
        Map<String, Integer> orderIndexById = new LinkedHashMap<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            orderIndexById.put(orderedIds.get(index), index);
        }
        hydrated.sort(
            Comparator.comparingInt(item -> orderIndexById.getOrDefault(toStringOrNull(item.get("id")), Integer.MAX_VALUE))
        );
        return hydrated;
    }

    private List<Map<String, Object>> fetchPlayableVideoRowsWithFallback(Map<String, String> params, String accessToken) {
        Map<String, String> requestParams = new LinkedHashMap<>(params);
        requestParams.putIfAbsent("media_status", "eq.ready");

        try {
            return supabaseRestClient.getList(
                "content",
                buildQuery(requestParams),
                accessToken,
                MAP_LIST
            );
        } catch (ResponseStatusException ex) {
            if (!shouldRetryWithoutMediaStatus(ex)) {
                throw ex;
            }
            requestParams.remove("media_status");
            return supabaseRestClient.getList(
                "content",
                buildQuery(requestParams),
                accessToken,
                MAP_LIST
            );
        }
    }

    private List<Map<String, Object>> hydrateContentItems(List<Map<String, Object>> items, UUID userId, String accessToken) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> hydrated = new ArrayList<>(items);
        List<Map<String, Object>> decorated = contentEngagementService.decorateItemsWithUserEngagement(hydrated, userId, accessToken);
        List<Map<String, Object>> enriched = contentCreatorEnrichmentService.enrichWithCreatorProfiles(decorated);
        attachTagsToItems(enriched);
        enriched.forEach(this::attachStreamFields);
        return enriched;
    }

    private void attachTagsToItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Set<String> contentIds = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String contentId = toStringOrNull(item == null ? null : item.get("id"));
            if (contentId != null && !contentId.isBlank()) {
                contentIds.add(contentId);
            }
        }
        if (contentIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> tagRows = supabaseAdminRestClient.getList(
            "content_tags",
            buildQuery(Map.of(
                "select", "content_id,tag",
                "content_id", "in.(" + String.join(",", contentIds) + ")"
            )),
            MAP_LIST
        );

        Map<String, List<String>> tagsByContentId = new LinkedHashMap<>();
        for (Map<String, Object> row : tagRows) {
            String contentId = toStringOrNull(row.get("content_id"));
            String tag = normalizeNullableText(toStringOrNull(row.get("tag")));
            if (contentId == null || tag == null) {
                continue;
            }
            tagsByContentId.computeIfAbsent(contentId, ignored -> new ArrayList<>()).add(tag);
        }

        for (Map<String, Object> item : items) {
            String contentId = toStringOrNull(item.get("id"));
            item.put("tags", tagsByContentId.getOrDefault(contentId, List.of()));
        }
    }

    private Comparator<Map<String, Object>> buildSimilarContentComparator(Map<String, Integer> sharedTagCounts) {
        return (left, right) -> {
            String leftId = toStringOrNull(left.get("id"));
            String rightId = toStringOrNull(right.get("id"));
            int sharedCompare = Integer.compare(
                sharedTagCounts.getOrDefault(rightId, 0),
                sharedTagCounts.getOrDefault(leftId, 0)
            );
            if (sharedCompare != 0) {
                return sharedCompare;
            }

            OffsetDateTime leftCreatedAt = parseOffsetDateTime(left.get("created_at"));
            OffsetDateTime rightCreatedAt = parseOffsetDateTime(right.get("created_at"));
            if (leftCreatedAt != null && rightCreatedAt != null) {
                int createdAtCompare = rightCreatedAt.compareTo(leftCreatedAt);
                if (createdAtCompare != 0) {
                    return createdAtCompare;
                }
            } else if (leftCreatedAt != null) {
                return -1;
            } else if (rightCreatedAt != null) {
                return 1;
            }

            String normalizedLeftId = leftId == null ? "" : leftId;
            String normalizedRightId = rightId == null ? "" : rightId;
            return normalizedRightId.compareTo(normalizedLeftId);
        };
    }

    private int normalizeSimilarLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_SIMILAR_LIMIT;
        }
        return Math.min(MAX_SIMILAR_LIMIT, limit);
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

    public void recordPlaybackEvent(UUID userId, UUID contentId, ContentPlaybackEventRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        ensureContentExists(contentId);

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("user_id", userId);
        insert.put("startup_ms", request.startupMs());
        insert.put("stall_count", request.stallCount());
        insert.put("stalled_ms", request.stalledMs());
        insert.put("watch_ms", request.watchMs());
        insert.put("play_success", request.playSuccess());
        insert.put("autoplay_blocked_count", request.autoplayBlockedCount());
        insert.put("network_type", normalizeNullableText(request.networkType()));
        insert.put("user_agent", normalizeNullableText(request.userAgent()));
        insert.put("created_at", OffsetDateTime.now());

        try {
            supabaseAdminRestClient.postList("content_playback_events", insert, MAP_LIST);
        } catch (ResponseStatusException ex) {
            log.debug("Failed to persist playback event for content {}: {}", contentId, ex.getReason());
        }
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

        List<Map<String, Object>> existingFlags = supabaseRestClient.getList(
            "content_flags",
            buildQuery(Map.of(
                "select", "id",
                "content_id", "eq." + contentId,
                "reported_by", "eq." + userId,
                "status", "eq.pending",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (!existingFlags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already flagged this content");
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("content_id", contentId);
        insert.put("reported_by", userId);
        insert.put("reason", request.reason());
        insert.put("description", normalizeNullableText(request.description()));
        insert.put("created_at", OffsetDateTime.now());

        supabaseRestClient.postList("content_flags", insert, token, MAP_LIST);
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

    public void deleteComment(UUID userId, UUID contentId, UUID commentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureUserAndContent(userId, contentId);
        if (commentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment id is required");
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content_comments",
            buildQuery(Map.of(
                "select", "id,user_id",
                "id", "eq." + commentId,
                "content_id", "eq." + contentId,
                "is_deleted", "eq.false",
                "limit", "1"
            )),
            MAP_LIST
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        UUID ownerId = parseUuid(rows.get(0).get("user_id"));
        boolean isOwner = ownerId != null && ownerId.equals(userId);
        boolean isAdmin = userService.getRoles(userId, token).contains(AppRole.ADMIN);
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this comment");
        }

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("is_deleted", true);
        patch.put("updated_at", OffsetDateTime.now());

        supabaseAdminRestClient.patchList(
            "content_comments",
            buildQuery(Map.of(
                "id", "eq." + commentId,
                "content_id", "eq." + contentId,
                "is_deleted", "eq.false"
            )),
            patch,
            MAP_LIST
        );

        refreshEngagementCounts(contentId);
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

    private void attachStreamFields(Map<String, Object> item) {
        if (item == null) {
            return;
        }
        String mediaUrl = toStringOrNull(item.get("media_url"));
        if (mediaUrl == null || mediaUrl.isBlank()) {
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

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeTags(Object value) {
        if (!(value instanceof List<?> tags)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object tag : tags) {
            String text = normalizeNullableText(toStringOrNull(tag));
            if (text != null) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private String escapeQuery(String query) {
        return query.replace(",", " ").replace("(", " ").replace(")", " ");
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

package com.rotiprata.application;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class ContentService {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CONTENT_TYPE = "content_type";
    private static final String STATUS = "status";
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;

    public ContentService(SupabaseRestClient supabaseRestClient, SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    public LikeContentResult likeContent(UUID userId, UUID contentId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,likes_count",
                "id", "eq." + contentId
            )),
            MAP_LIST
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

        int currentCount = toInt(rows.get(0).get("likes_count"));
        List<Map<String, Object>> existingLike = supabaseAdminRestClient.getList(
            "content_likes",
            buildQuery(Map.of(
                "select", "id",
                "content_id", "eq." + contentId,
                "user_id", "eq." + userId,
                "limit", "1"
            )),
            MAP_LIST
        );

        if (!existingLike.isEmpty()) {
            return new LikeContentResult(currentCount, true);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content_id", contentId);
        payload.put("user_id", userId);
        payload.put("created_at", OffsetDateTime.now());
        supabaseAdminRestClient.postList("content_likes", payload, MAP_LIST);

        int nextCount = Math.max(0, currentCount + 1);
        List<Map<String, Object>> updated = supabaseAdminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            Map.of(
                "likes_count", nextCount,
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );

        int resolvedCount = updated.isEmpty() ? nextCount : toInt(updated.get(0).get("likes_count"));
        return new LikeContentResult(resolvedCount, true);
    }

    public LikeContentResult unlikeContent(UUID userId, UUID contentId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,likes_count",
                "id", "eq." + contentId
            )),
            MAP_LIST
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

        int currentCount = toInt(rows.get(0).get("likes_count"));
        List<Map<String, Object>> deletedLikes = supabaseAdminRestClient.deleteList(
            "content_likes",
            buildQuery(Map.of(
                "content_id", "eq." + contentId,
                "user_id", "eq." + userId
            )),
            MAP_LIST
        );

        if (deletedLikes.isEmpty()) {
            return new LikeContentResult(Math.max(0, currentCount), false);
        }

        int decrement = Math.max(1, deletedLikes.size());
        int nextCount = Math.max(0, currentCount - decrement);
        List<Map<String, Object>> updated = supabaseAdminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            Map.of(
                "likes_count", nextCount,
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );

        int resolvedCount = updated.isEmpty() ? nextCount : toInt(updated.get(0).get("likes_count"));
        return new LikeContentResult(resolvedCount, false);
    }

    public void saveContent(UUID userId, UUID contentId, String accessToken) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        String token = requireAccessToken(accessToken);

        List<Map<String, Object>> contentRows = supabaseRestClient.getList(
            "/content",
            buildQuery(Map.of("select", "id", "id", "eq." + contentId, "limit", "1")),
            token,
            MAP_LIST
        );
        if (contentRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

        List<Map<String, Object>> existing = supabaseRestClient.getList(
            "/saved_content",
            buildQuery(Map.of(
                "select", "id",
                "user_id", "eq." + userId,
                "content_id", "eq." + contentId,
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (!existing.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("content_id", contentId);
        payload.put("saved_at", OffsetDateTime.now());
        supabaseRestClient.postList("/saved_content", payload, token, MAP_LIST);
    }

    public void unsaveContent(UUID userId, UUID contentId, String accessToken) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        String token = requireAccessToken(accessToken);

        supabaseRestClient.deleteList(
            "/saved_content",
            buildQuery(Map.of(
                "user_id", "eq." + userId,
                "content_id", "eq." + contentId
            )),
            token,
            MAP_LIST
        );
    }

    public List<Map<String, Object>> getSavedContent(UUID userId, String accessToken) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        String token = requireAccessToken(accessToken);
        return supabaseRestClient.getList(
            "/saved_content",
            buildQuery(Map.of(
                "select", "id,saved_at,content_id,content:content_id(id,title,description,media_url,thumbnail_url,content_type,likes_count,created_at)",
                "user_id", "eq." + userId,
                "content_id", "not.is.null",
                "order", "saved_at.desc"
            )),
            token,
            MAP_LIST
        );
    }

    public Map<String, Object> getContentQuiz(UUID contentId, String accessToken) {
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        String token = requireAccessToken(accessToken);

        List<Map<String, Object>> quizzes = supabaseRestClient.getList(
            "/quizzes",
            buildQuery(Map.of(
                "select", "id,lesson_id,content_id,title,description,quiz_type,time_limit_seconds,passing_score,created_by,created_at,updated_at,is_active,archived_at",
                "content_id", "eq." + contentId,
                "is_active", "eq.true",
                "order", "created_at.desc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (quizzes.isEmpty()) {
            return null;
        }

        Map<String, Object> quiz = new LinkedHashMap<>(quizzes.get(0));
        Object quizId = quiz.get("id");
        if (quizId == null) {
            return quiz;
        }

        List<Map<String, Object>> questions = supabaseRestClient.getList(
            "/quiz_questions",
            buildQuery(Map.of(
                "select", "id,quiz_id,question_text,question_type,media_url,options,correct_answer,explanation,points,order_index,created_at",
                "quiz_id", "eq." + quizId,
                "order", "order_index.asc"
            )),
            token,
            MAP_LIST
        );
        quiz.put("questions", questions);
        return quiz;
    }

    public Map<String, Object> submitContentQuizResult(
        UUID userId,
        UUID contentId,
        int score,
        int maxScore,
        Map<String, Object> answers,
        Integer timeTakenSeconds,
        String accessToken
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        String token = requireAccessToken(accessToken);
        Map<String, Object> quiz = getContentQuiz(contentId, token);
        if (quiz == null || quiz.get("id") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found for content");
        }

        int normalizedMax = Math.max(1, maxScore);
        int normalizedScore = Math.max(0, Math.min(score, normalizedMax));
        double percentage = (normalizedScore * 100.0) / normalizedMax;
        int passingScore = toInt(quiz.get("passing_score"));
        boolean passed = percentage >= Math.max(0, passingScore);

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("quiz_id", quiz.get("id"));
        insert.put("score", normalizedScore);
        insert.put("max_score", normalizedMax);
        insert.put("percentage", percentage);
        insert.put("passed", passed);
        insert.put("answers", answers == null ? null : answers);
        insert.put("time_taken_seconds", timeTakenSeconds);
        insert.put("attempted_at", OffsetDateTime.now());

        List<Map<String, Object>> created = supabaseRestClient.postList("/user_quiz_results", insert, token, MAP_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to save quiz result");
        }
        return created.get(0);
    }

    public Map<String, Object> getLatestContentQuizResult(UUID userId, UUID contentId, String accessToken) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        String token = requireAccessToken(accessToken);
        Map<String, Object> quiz = getContentQuiz(contentId, token);
        if (quiz == null || quiz.get("id") == null) {
            return null;
        }

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "/user_quiz_results",
            buildQuery(Map.of(
                "select", "score,max_score,percentage,passed,answers,time_taken_seconds,attempted_at",
                "user_id", "eq." + userId,
                "quiz_id", "eq." + quiz.get("id"),
                "order", "attempted_at.desc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return rows.isEmpty() ? null : rows.get(0);
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
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,view_count",
                "id", "eq." + contentId
            )),
            MAP_LIST
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

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

    public record LikeContentResult(int likesCount, boolean liked) {}
}

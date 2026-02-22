package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.LessonFeedRequest;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.api.dto.LessonSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;

    public LessonService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public LessonFeedResponse getLessonFeed(String accessToken, LessonFeedRequest request) {
        String token = requireAccessToken(accessToken);
        int page = request == null ? DEFAULT_PAGE : normalizePage(request.page());
        int pageSize = request == null ? DEFAULT_PAGE_SIZE : normalizePageSize(request.pageSize());
        int offset = (page - 1) * pageSize;
        int limit = pageSize + 1;

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("select", "*");
        params.put("is_published", "eq.true");
        applyQueryFilter(request == null ? null : request.query(), params);
        applyDifficultyFilter(request == null ? null : request.difficulty(), params);
        applyDurationFilter(request == null ? null : request.duration(), params);
        params.put("order", resolveSort(request == null ? null : request.sort()));
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "lessons",
            buildQuery(params),
            token,
            MAP_LIST
        );
        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, pageSize) : rows;
        return new LessonFeedResponse(items, hasMore, page, pageSize);
    }

    public List<ContentSearchDTO> searchLessons(String query, String accessToken) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmedQuery = query.trim();
        String safeQuery = escapeQuery(trimmedQuery);
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("select", "id,title,description");
        params.put("is_published", "eq.true");
        params.put(
            "or",
            String.format(
                "(title.ilike.*%s*,description.ilike.*%s*)",
                safeQuery,
                safeQuery
            )
        );
        params.put("limit", "10");

        List<LessonSearchDTO> rows = supabaseRestClient.getList(
            "lessons",
            buildQuery(params),
            accessToken,
            new TypeReference<List<LessonSearchDTO>>() {}
        );

        return rows.stream()
            .map(row -> {
                String desc = row.description();
                String snippet = (desc != null && desc.length() > 100)
                    ? desc.substring(0, 100) + "..."
                    : desc;
                return new ContentSearchDTO(
                    row.id(),
                    "lesson",
                    row.title(),
                    row.description(),
                    snippet
                );
            })
            .toList();
    }

    private String escapeQuery(String query) {
        return query.replace(",", " ").replace("(", " ").replace(")", " ");
    }

    private void applyQueryFilter(String query, LinkedHashMap<String, String> params) {
        if (query == null || query.isBlank()) {
            return;
        }
        String safeQuery = escapeQuery(query.trim());
        params.put(
            "or",
            String.format(
                "(title.ilike.*%s*,description.ilike.*%s*,summary.ilike.*%s*)",
                safeQuery,
                safeQuery,
                safeQuery
            )
        );
    }

    private void applyDifficultyFilter(String difficulty, LinkedHashMap<String, String> params) {
        String normalized = difficulty == null ? "all" : difficulty.trim().toLowerCase();
        switch (normalized) {
            case "beginner", "1" -> params.put("difficulty_level", "eq.1");
            case "intermediate", "2" -> params.put("difficulty_level", "eq.2");
            case "advanced", "3" -> params.put("difficulty_level", "eq.3");
            case "all", "" -> {
                return;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid difficulty filter");
        }
    }

    private void applyDurationFilter(String duration, LinkedHashMap<String, String> params) {
        String normalized = duration == null ? "all" : duration.trim().toLowerCase();
        switch (normalized) {
            case "short" -> params.put("estimated_minutes", "lte.10");
            case "medium" -> params.put("and", "(estimated_minutes.gte.11,estimated_minutes.lte.20)");
            case "long" -> params.put("estimated_minutes", "gte.21");
            case "all", "" -> {
                return;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid duration filter");
        }
    }

    private String resolveSort(String sort) {
        String normalized = sort == null ? "popular" : sort.trim().toLowerCase();
        return switch (normalized) {
            case "popular", "" -> "completion_count.desc,created_at.desc";
            case "newest" -> "created_at.desc";
            case "shortest" -> "estimated_minutes.asc";
            case "highest_xp" -> "xp_reward.desc";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort option");
        };
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(MAX_PAGE_SIZE, pageSize);
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
}

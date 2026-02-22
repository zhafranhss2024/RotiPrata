package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonFeedService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SUPPORTED_SORTS = Set.of(
        "newest",
        "popular",
        "duration_asc",
        "duration_desc"
    );

    private final SupabaseRestClient supabaseRestClient;

    public LessonFeedService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public LessonFeedResponse getLessonFeed(
        String accessToken,
        int page,
        Integer pageSize,
        String query,
        Integer difficulty,
        Integer maxMinutes,
        String sort
    ) {
        String token = requireAccessToken(accessToken);
        int safePage = Math.max(1, page);
        int safePageSize = normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("select", "*");
        params.put("is_published", "eq.true");

        if (difficulty != null) {
            validateDifficulty(difficulty);
            params.put("difficulty_level", "eq." + difficulty);
        }
        if (maxMinutes != null) {
            validateMaxMinutes(maxMinutes);
            params.put("estimated_minutes", "lte." + maxMinutes);
        }

        String sanitizedQuery = sanitizeQuery(query);
        if (sanitizedQuery != null) {
            params.put(
                "or",
                String.format(
                    "(title.ilike.*%s*,description.ilike.*%s*)",
                    sanitizedQuery,
                    sanitizedQuery
                )
            );
        }

        params.put("order", resolveOrder(sort));
        params.put("limit", String.valueOf(safePageSize + 1));
        params.put("offset", String.valueOf(offset));

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "lessons",
            buildQuery(params),
            token,
            MAP_LIST
        );

        boolean hasMore = rows.size() > safePageSize;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, safePageSize) : rows;

        return new LessonFeedResponse(items, hasMore, safePage, safePageSize);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be at least 1");
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void validateDifficulty(int difficulty) {
        if (difficulty < 1 || difficulty > 3) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "difficulty must be one of 1, 2, or 3"
            );
        }
    }

    private void validateMaxMinutes(int maxMinutes) {
        if (maxMinutes < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxMinutes must be at least 1");
        }
    }

    private String resolveOrder(String sort) {
        String normalized = (sort == null || sort.isBlank()) ? "newest" : sort.trim().toLowerCase();
        if (!SUPPORTED_SORTS.contains(normalized)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "sort must be one of newest, popular, duration_asc, duration_desc"
            );
        }

        return switch (normalized) {
            case "popular" -> "completion_count.desc";
            case "duration_asc" -> "estimated_minutes.asc";
            case "duration_desc" -> "estimated_minutes.desc";
            default -> "created_at.desc";
        };
    }

    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String cleaned = query
            .trim()
            .replace(",", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace("*", " ")
            .replace("%", " ")
            .replace("&", " ");
        String collapsed = cleaned.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
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

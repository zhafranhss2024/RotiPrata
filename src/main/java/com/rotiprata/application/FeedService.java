package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.FeedResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FeedService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int PAGE_SIZE = 20;

    private final SupabaseRestClient supabaseRestClient;

    public FeedService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public FeedResponse getFeed(String accessToken, UUID userId, int page) {
        String token = requireAccessToken(accessToken);
        int pageNumber = Math.max(1, page);
        int offset = (pageNumber - 1) * PAGE_SIZE;
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "*",
                "status", "eq.approved",
                "order", "created_at.desc",
                "limit", String.valueOf(PAGE_SIZE + 1),
                "offset", String.valueOf(offset)
            )),
            token,
            MAP_LIST
        );
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        enrichLikeState(items, userId, token);
        return new FeedResponse(items, hasMore);
    }

    private void enrichLikeState(List<Map<String, Object>> items, UUID userId, String token) {
        if (items.isEmpty() || userId == null) {
            return;
        }

        Set<String> contentIds = new LinkedHashSet<>();
        for (Map<String, Object> row : items) {
            Object id = row.get("id");
            if (id != null) {
                contentIds.add(id.toString());
            }
        }
        if (contentIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> likes = supabaseRestClient.getList(
            "content_likes",
            buildQuery(Map.of(
                "select", "content_id",
                "user_id", "eq." + userId,
                "content_id", "in.(" + String.join(",", contentIds) + ")"
            )),
            token,
            MAP_LIST
        );
        Set<String> likedContentIds = new LinkedHashSet<>();
        for (Map<String, Object> likeRow : likes) {
            Object contentId = likeRow.get("content_id");
            if (contentId != null) {
                likedContentIds.add(contentId.toString());
            }
        }

        for (Map<String, Object> row : items) {
            Object id = row.get("id");
            row.put("liked_by_me", id != null && likedContentIds.contains(id.toString()));
        }
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

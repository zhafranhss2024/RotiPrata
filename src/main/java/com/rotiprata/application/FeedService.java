package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.FeedResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.List;
import java.util.Map;
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

    public FeedResponse getFeed(String accessToken, int page) {
        String token = requireAccessToken(accessToken);
        int pageNumber = Math.max(1, page);
        int offset = (pageNumber - 1) * PAGE_SIZE;
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "*",
                "or", "(status.eq.approved,status.eq.accepted)",
                "order", "created_at.desc",
                "limit", String.valueOf(PAGE_SIZE + 1),
                "offset", String.valueOf(offset)
            )),
            token,
            MAP_LIST
        );
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        return new FeedResponse(items, hasMore);
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

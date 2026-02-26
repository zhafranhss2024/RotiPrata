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
}

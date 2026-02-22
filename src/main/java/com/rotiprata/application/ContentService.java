package com.rotiprata.application;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class ContentService {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CONTENT_TYPE = "content_type";
    private static final String STATUS = "status";
    

    private final SupabaseRestClient supabaseRestClient;

    public ContentService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
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
}

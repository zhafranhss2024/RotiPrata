package com.rotiprata.application;

import java.util.List;

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

    private final SupabaseRestClient supabaseRestClient;

    public ContentService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<ContentSearchDTO> getFilteredContent(String query, String filter, String accessToken) {
        String path = "/content";

        String selectColumns = String.join(",", ID, TITLE, DESCRIPTION, CONTENT_TYPE);
        String filterQuery = String.format("select=%s&title=ilike.*%s*&limit=10", selectColumns, query.trim());

        if (filter != null && !filter.isEmpty()) {
            filterQuery += "&content_type=eq." + filter.toLowerCase();
        }

        List<ContentSearchDTO> resultsFromDb = supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<ContentSearchDTO>>() {}
        );

        return resultsFromDb.stream()
            .map(c -> {
                String desc = c.description();
                String snippet = (desc != null && desc.length() > 100)
                    ? desc.substring(0, 100) + "..."
                    : desc;

                return new ContentSearchDTO(
                    c.id(),
                    c.title(),
                    c.contentType(),
                    null,   
                    snippet
                );
            })
            .toList();
    }
}

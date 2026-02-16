package com.rotiprata.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class ContentService {

    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CONTENT_TYPE = "content_type";

    private final SupabaseRestClient supabaseRestClient;

    public ContentService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<ContentSearchDTO> getFilteredContent(String query, String filter, String accessToken) {
        String path = "/content";

        String selectColumns = String.join(",", TITLE, DESCRIPTION, CONTENT_TYPE);

        String filterQuery = String.format("select=%s&%s=ilike.*%s*", selectColumns, TITLE, query);

        if (filter != null && !filter.isEmpty()) {
            filterQuery += "&" + CONTENT_TYPE + "=eq." + filter;
        }

        return supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<ContentSearchDTO>>() {}
        );
    }
}


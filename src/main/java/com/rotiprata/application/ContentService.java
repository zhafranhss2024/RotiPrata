package com.rotiprata.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.domain.Content;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class ContentService {

    private final SupabaseRestClient supabaseRestClient;

    public ContentService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<Content> getFilteredContent(String query, String[] filter, String accessToken) {
        String path = "/content";

        String filterQuery = "title=ilike.*" + query + "*";
        if (filter != null && filter.length > 0) {
            for (String f : filter) {
                filterQuery += "&type=eq." + f;
            }
        }

        return supabaseRestClient.getList(
            path,
            filterQuery,
            accessToken,
            new TypeReference<List<Content>>() {}
        );
    }
}

package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.LessonSearchDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LessonService {
    private final SupabaseRestClient supabaseRestClient;

    public LessonService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<ContentSearchDTO> searchLessons(String query, String accessToken) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmedQuery = query.trim();
        String safeQuery = escapeQuery(trimmedQuery);
        String path = "/lessons";
        String filterQuery = String.format(
            "select=id,title,description&is_published=eq.true&or=(title.ilike.*%s*,description.ilike.*%s*)&limit=10",
            safeQuery,
            safeQuery
        );

        List<LessonSearchDTO> rows = supabaseRestClient.getList(
            path,
            filterQuery,
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
}

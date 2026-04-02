package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.GetHistoryDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.rotiprata.api.dto.SaveHistoryDTO;

@Service
public class BrowsingService {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final SupabaseRestClient supabaseRestClient;

    public BrowsingService(
            ContentService contentService,
            LessonService lessonService,
            SupabaseRestClient supabaseRestClient
    ) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.supabaseRestClient = supabaseRestClient;
    }

    // ================= SEARCH =================

    public List<ContentSearchDTO> search(
            String query,
            String filter,
            String accessToken
    ) {
        List<ContentSearchDTO> results = new ArrayList<>();
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase();

        if (normalizedFilter.isBlank()) {
            results.addAll(contentService.getFilteredContent(query, null, accessToken));
            results.addAll(mapLessonsToSearchResults(
                    lessonService.searchLessons(query, accessToken)
            ));
            return results;
        }

        if ("video".equals(normalizedFilter)) {
            results.addAll(contentService.getFilteredContent(query, "video", accessToken));
            return results;
        }

        if ("lesson".equals(normalizedFilter)) {
            results.addAll(mapLessonsToSearchResults(
                    lessonService.searchLessons(query, accessToken)
            ));
        }

        return results;
    }

    // ================= SAVE HISTORY =================

    public void saveHistory(
            String userId,
            String query,
            Instant searchedAt,
            String accessToken
    ) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return;
        }

        SaveHistoryDTO dto = new SaveHistoryDTO();
        dto.setUserId(userId);
        dto.setQuery(normalizedQuery);
        dto.setSearchedAt(searchedAt != null ? searchedAt : Instant.now());
        String existingQuery = UriComponentsBuilder.newInstance()
                .queryParam("user_id", "eq." + userId)
                .queryParam("query", "eq." + normalizedQuery)
                .queryParam("order", "searched_at.desc")
                .queryParam("limit", "1")
                .build()
                .encode()
                .toUriString()
                .replaceFirst("^\\?", "");

        List<Map<String, Object>> existing = supabaseRestClient.getList(
                "search_history",
                existingQuery,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );

        if (!existing.isEmpty()) {
            String id = existing.get(0).get("id") == null ? null : existing.get(0).get("id").toString();
            if (id != null && !id.isBlank()) {
                supabaseRestClient.patchList(
                        "search_history",
                        UriComponentsBuilder.newInstance()
                                .queryParam("id", "eq." + id)
                                .queryParam("user_id", "eq." + userId)
                                .build()
                                .encode()
                                .toUriString()
                                .replaceFirst("^\\?", ""),
                        Map.of("searched_at", dto.getSearchedAt()),
                        accessToken,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                return;
            }
        }

        supabaseRestClient.postList(
                "search_history",
                dto,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }

    // ================= FETCH HISTORY =================

    public List<GetHistoryDTO> fetchHistory(
        String userId, String accessToken
    ) {
        String query = "user_id=eq." + userId + "&order=searched_at.desc&limit=5";

        return supabaseRestClient.getList(
                "search_history",
                query,
                accessToken,
                new TypeReference<List<GetHistoryDTO>>() {}
        );
    }

    // ================= CLEAR HISTORY =================

    public void deleteHistoryById(
            String id,
            String userId,
            String accessToken
    ) {
        if (id == null || id.isBlank()) {
            return;
        }

        String query = "id=eq." + id + "&user_id=eq." + userId;

        supabaseRestClient.deleteList(
                "search_history",
                query,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }

    // ================= INTERNAL HELPERS =================

    private List<ContentSearchDTO> mapLessonsToSearchResults(
            List<Map<String, Object>> lessons
    ) {
        List<ContentSearchDTO> results = new ArrayList<>();

        for (Map<String, Object> lesson : lessons) {
            String id = toStringValue(lesson.get("id"));
            String title = toStringValue(lesson.get("title"));
            String description = toStringValue(lesson.get("description"));

            results.add(new ContentSearchDTO(
                    id,
                    "lesson",
                    title,
                    description,
                    buildSnippet(description)
            ));
        }

        return results;
    }

    private String buildSnippet(String description) {
        if (description == null) return null;
        return description.length() > 100
                ? description.substring(0, 100) + "..."
                : description;
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}

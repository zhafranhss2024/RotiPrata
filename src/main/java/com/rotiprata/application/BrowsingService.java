package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.GetHistoryDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            String title,
            Instant searchedAt,
            String accessToken
    ) {
        if (query == null || query.isEmpty()) {
            return; 
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", userId);
        payload.put("query", query);
        payload.put("title", title);
        payload.put("searched_at", searchedAt != null ? searchedAt : Instant.now());

        String conflict = "on_conflict=user_id,query";

        supabaseRestClient.upsertList(
                "search_history",
                conflict,
                payload,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }

    // ================= FETCH HISTORY =================

    public List<GetHistoryDTO> fetchHistory(
            String userId,
            String accessToken
    ) {
        String query = "user_id=eq." + userId + "&order=viewed_at.desc&limit=20";

        List<Map<String, Object>> rows = supabaseRestClient.getList(
                "browsing_history",
                query,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );

        List<GetHistoryDTO> history = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            GetHistoryDTO item = new GetHistoryDTO();
            item.setId(toStringValue(row.get("id")));
            item.setItemId(toStringValue(row.get("item_id")));
            item.setTitle(toStringValue(row.get("title")));
            item.setContentId(toStringValue(row.get("content_id")));
            item.setLessonId(toStringValue(row.get("lesson_id")));

            Object viewedAt = row.get("viewed_at");
            if (viewedAt != null) {
                item.setViewedAt(Instant.parse(viewedAt.toString()));
            }

            history.add(item);
        }

        return history;
    }

    // ================= CLEAR HISTORY =================

    public void purgeHistory(
            String userId,
            String accessToken
    ) {
        supabaseRestClient.deleteList(
                "browsing_history",
                "user_id=eq." + userId,
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

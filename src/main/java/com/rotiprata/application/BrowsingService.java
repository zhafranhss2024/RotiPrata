package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.SaveHistoryDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BrowsingService {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final SupabaseRestClient supabaseRestClient;

    public BrowsingService(ContentService contentService, LessonService lessonService, SupabaseRestClient supabaseRestClient) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<ContentSearchDTO> search(String query, String filter, String accessToken) {

        List<ContentSearchDTO> results = new ArrayList<>();
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase();

        if (normalizedFilter.isBlank()) {
            results.addAll(contentService.getFilteredContent(query, null, accessToken));
            results.addAll(mapLessonsToSearchResults(lessonService.searchLessons(query, accessToken)));
            return results;
        }

        if ("video".equals(normalizedFilter)) {
            results.addAll(contentService.getFilteredContent(query, "video", accessToken));
            return results;
        }

        if ("lesson".equals(normalizedFilter)) {
            results.addAll(mapLessonsToSearchResults(lessonService.searchLessons(query, accessToken)));
            return results;
        }

        return results;
    }

    private List<ContentSearchDTO> mapLessonsToSearchResults(List<Map<String, Object>> lessons) {
        List<ContentSearchDTO> lessonResults = new ArrayList<>();
        for (Map<String, Object> lesson : lessons) {
            String id = toStringValue(lesson.get("id"));
            String title = toStringValue(lesson.get("title"));
            String description = toStringValue(lesson.get("description"));
            lessonResults.add(new ContentSearchDTO(id, "lesson", title, description, buildSnippet(description)));
        }
        return lessonResults;
    }

    private String buildSnippet(String description) {
        if (description == null) {
            return null;
        }
        return description.length() > 100 ? description.substring(0, 100) + "..." : description;
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public void saveHistory(String contentId, String lessonId, String accessToken) {
        if (contentId == null && lessonId == null) {
            return;
        }

        String itemId = contentId != null ? contentId : lessonId;

        SaveHistoryDTO dto = new SaveHistoryDTO();
        dto.setItemId(itemId);
        dto.setContentId(contentId);
        dto.setLessonId(lessonId);
        dto.setViewedAt(Instant.now());

        String query = "on_conflict=user_id,item_id";

        supabaseRestClient.upsertList(
            "browsing_history",
            query,
            dto,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );
    }


    public void saveHistory(String userId, String contentId, String lessonId, String accessToken) {
        saveHistory(contentId, lessonId, accessToken);
    }

    public List<SaveHistoryDTO> getHistory(String userId, String accessToken) {
        String query = "user_id=eq." + userId + "&order=viewed_at.desc&limit=20";
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "browsing_history",
            query,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );

        List<SaveHistoryDTO> history = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SaveHistoryDTO item = new SaveHistoryDTO();
            item.setItemId(toStringValue(row.get("item_id")));
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

    public void clearHistory(String userId, String accessToken) {
        supabaseRestClient.deleteList(
            "browsing_history",
            "user_id=eq." + userId,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );
    }
}

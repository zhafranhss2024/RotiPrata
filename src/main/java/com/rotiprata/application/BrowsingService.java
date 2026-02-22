package com.rotiprata.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.SaveHistoryDTO;
import com.rotiprata.api.dto.SaveHistoryRequestDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import com.fasterxml.jackson.core.type.TypeReference;
// import com.rotiprata.api.dto.BrowsingHistoryDTO;


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
            results.addAll(lessonService.searchLessons(query, accessToken));
            return results;
        }

        if ("video".equals(normalizedFilter)) {
            results.addAll(contentService.getFilteredContent(query, "video", accessToken));
            return results;
        }

        if ("lesson".equals(normalizedFilter)) {
            results.addAll(lessonService.searchLessons(query, accessToken));
            return results;
        }

        return results;
    }

    public void saveHistory(String contentId, String lessonId, String accessToken) {
        if (contentId == null && lessonId == null) return;

        String itemId = contentId != null ? contentId : lessonId;

        SaveHistoryDTO dto = new SaveHistoryDTO();
        dto.setItemId(itemId);
        dto.setContentId(contentId);
        dto.setLessonId(lessonId);
        dto.setViewedAt(Instant.now());

        String path = "/browsing_history";
        String query = "on_conflict=user_id,item_id";

        supabaseRestClient.upsertList(
            path,
            query,
            dto,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );   

    }

    // public List<SaveHistoryDTO> getHistory(String userId, String accessToken) {

    //     String path = "/browsing_history?user_id=eq." + userId + "&order=viewed_at.desc&limit=5";

    //     System.out.println("user: " + userId);

    //     return supabaseRestClient.getList(
    //         path,
    //         null,
    //         accessToken,
    //         new TypeReference<List<SaveHistoryDTO>>() {}
    //     );
    // }


}

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
    private final SupabaseRestClient supabaseRestClient;

    public BrowsingService(ContentService contentService, SupabaseRestClient supabaseRestClient) {
        this.contentService = contentService;
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<ContentSearchDTO> search(String query, String filter, String accessToken) {

        List<ContentSearchDTO> results = new ArrayList<>();

        List<ContentSearchDTO> contents = contentService.getFilteredContent(query, filter, accessToken);
        results.addAll(contents);

        // TODO: Get lessons if Lesson extends Content
        // List<Lesson> lessons = lessonService.getFilteredLessons(query, filter, accessToken);
        // results.addAll(lessons);

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

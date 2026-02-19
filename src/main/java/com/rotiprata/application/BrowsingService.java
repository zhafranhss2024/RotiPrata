package com.rotiprata.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.api.dto.SaveHistoryDTO;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.BrowsingHistoryDTO;


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
        String path = "/browsing_history";

        if (contentId == null && lessonId == null) return;

        SaveHistoryDTO dto = new SaveHistoryDTO();
        if (contentId != null) dto.setContentId(contentId);
        if (lessonId != null) dto.setLessonId(lessonId);

        supabaseRestClient.postList(
                path,
                dto,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
        );

        System.out.println("OK");
    } 

    public List<BrowsingHistoryDTO> getHistory(String userId) {
        String path = "/browsing_history";

    }


}

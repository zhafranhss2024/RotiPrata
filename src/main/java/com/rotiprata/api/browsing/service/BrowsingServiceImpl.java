package com.rotiprata.api.browsing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.dto.GetHistoryDTO;
import com.rotiprata.api.browsing.dto.SaveHistoryDTO;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of BrowsingService.
 * Handles search operations and manages search history using Supabase.
 */
@Service
public class BrowsingServiceImpl implements BrowsingService {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final SupabaseRestClient supabaseRestClient;

    /**
     * Constructor for dependency injection.
     */
    public BrowsingServiceImpl(
            ContentService contentService,
            LessonService lessonService,
            SupabaseRestClient supabaseRestClient
    ) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.supabaseRestClient = supabaseRestClient;
    }

    /** Performs a search across content and lessons based on query and optional filter */
    @Override
    public List<ContentSearchDTO> search(String query, String filter, String accessToken) {
        List<ContentSearchDTO> results = new ArrayList<>();
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase();

        switch (normalizedFilter) {
            case "" -> {
                results.addAll(contentService.getFilteredContent(query, null, accessToken));
                results.addAll(mapLessonsToSearchResults(lessonService.searchLessons(query, accessToken)));
            }
            case "video" -> results.addAll(contentService.getFilteredContent(query, "video", accessToken));
            case "lesson" -> results.addAll(mapLessonsToSearchResults(lessonService.searchLessons(query, accessToken)));
            default -> {
                // Unknown filter: return empty results
            }
        }

        return results;
    }

    /** Saves or updates a user's search history entry */
    @Override
    public void saveHistory(String userId, String query, Instant searchedAt, String accessToken) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) return;

        SaveHistoryDTO dto = new SaveHistoryDTO();
        dto.setUserId(userId);
        dto.setQuery(normalizedQuery);
        dto.setSearchedAt(searchedAt != null ? searchedAt : Instant.now());

        String dbQuery = "on_conflict=user_id,query";

        try {
            supabaseRestClient.upsertList(
                    "search_history",
                    dbQuery,
                    List.of(dto),
                    accessToken,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to save search history for user " + userId, e);
        }
    }

    /** Fetches the last 5 search history entries for a user */
    @Override
    public List<GetHistoryDTO> fetchHistory(String userId, String accessToken) {
        String query = "user_id=eq." + userId + "&order=searched_at.desc&limit=5";
        try {
            return supabaseRestClient.getList(
                    "search_history",
                    query,
                    accessToken,
                    new TypeReference<List<GetHistoryDTO>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch search history for user " + userId, e);
        }
    }

    // ================= CLEAR HISTORY =================

    @Override
    public void deleteHistoryById(String id, String userId, String accessToken) {
        if (id == null || id.isBlank()) return;

        String query = "id=eq." + id + "&user_id=eq." + userId;
        try {
            supabaseRestClient.deleteList(
                    "search_history",
                    query,
                    accessToken,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete search history entry " + id + " for user " + userId, e);
        }
    }

    // ================= INTERNAL HELPERS =================

    /** Maps lesson objects to search result DTOs */
    private List<ContentSearchDTO> mapLessonsToSearchResults(List<Map<String, Object>> lessons) {
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

    /** Builds a short snippet from the description */
    private String buildSnippet(String description) {
        if (description == null) return null;
        return description.length() > 100
                ? description.substring(0, 100) + "..."
                : description;
    }


     /** Converts an object to string safely */
    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
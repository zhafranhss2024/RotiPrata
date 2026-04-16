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

    // Constant
    private static final String TABLE_HISTORY = "search_history";
    private static final String TYPE_LESSON = "lesson";
    private static final String TYPE_VIDEO = "video";

    private static final int SNIPPET_LENGTH = 100;

    /**
     * Creates a browsing service impl instance with its collaborators.
     */
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

    /**
     * Handles search.
     */
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
        case TYPE_VIDEO -> results.addAll(contentService.getFilteredContent(query, TYPE_VIDEO, accessToken));
        case TYPE_LESSON -> results.addAll(mapLessonsToSearchResults(lessonService.searchLessons(query, accessToken)));
        default -> {
            // Unknown filter: return empty results
        }
    }

        return results;
    }

    /**
     * Saves the history.
     */
    /** Upsert a user's search history entry */
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
                    TABLE_HISTORY,
                    dbQuery,
                    List.of(dto),
                    accessToken,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to save search history for user " + userId, e);
        }
    }

    /**
     * Fetches the history.
     */
    /** Fetches the last 5 search history entries for a user */
    @Override
    public List<GetHistoryDTO> fetchHistory(String userId, String accessToken) {
        String query = "user_id=eq." + userId + "&order=searched_at.desc&limit=5";
        try {
            return supabaseRestClient.getList(
                    TABLE_HISTORY,
                    query,
                    accessToken,
                    new TypeReference<List<GetHistoryDTO>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch search history for user " + userId, e);
        }
    }

    // ================= CLEAR HISTORY =================

    /** Deletes a search history entry by its ID, scoped to the given user */
    @Override
    public void deleteHistoryById(String id, String userId, String accessToken) {
        if (id == null || id.isBlank()) return;

        String query = "id=eq." + id + "&user_id=eq." + userId;
        try {
            supabaseRestClient.deleteList(
                    TABLE_HISTORY,
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

    /**
     * Builds the snippet.
     */
    /** Builds a short snippet from the description */
    private String buildSnippet(String description) {
        if (description == null) return null;
        return description.length() > SNIPPET_LENGTH
                ? description.substring(0, SNIPPET_LENGTH) + "..."
                : description;
    }


     /**
      * Converts the value into string value.
      */
     /** Converts an object to string safely */
    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }
}

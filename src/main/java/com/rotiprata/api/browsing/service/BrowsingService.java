package com.rotiprata.api.browsing.service;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.dto.GetHistoryDTO;

import java.time.Instant;
import java.util.List;

/**
 * Service interface for browsing-related operations.
 * Provides search, history tracking, and history management features.
 */
public interface BrowsingService {

    /**
     * Searches for content and lessons based on a query and optional filter.
     *
     * @param query       the search query string
     * @param filter      optional filter, e.g., "video" or "lesson"
     * @param accessToken user's access token for authorization
     * @return a list of search results matching the query and filter
     */
    List<ContentSearchDTO> search(String query, String filter, String accessToken);

    /**
     * Saves a search query into the user's history.
     * If the query already exists, updates the timestamp instead of creating a duplicate.
     *
     * @param userId      the user's ID
     * @param query       the search query string
     * @param searchedAt  the timestamp of the search (optional, default to now)
     * @param accessToken user's access token for authorization
     */
    void saveHistory(String userId, String query, Instant searchedAt, String accessToken);

    /**
     * Fetches the most recent search history entries for a user.
     *
     * @param userId      the user's ID
     * @param accessToken user's access token for authorization
     * @return a list of recent search history entries (limited to 5)
     */
    List<GetHistoryDTO> fetchHistory(String userId, String accessToken);

    /**
     * Deletes a specific search history entry by its ID for a given user.
     *
     * @param id          the history entry ID to delete
     * @param userId      the user's ID
     * @param accessToken user's access token for authorization
     */
    void deleteHistoryById(String id, String userId, String accessToken);
}
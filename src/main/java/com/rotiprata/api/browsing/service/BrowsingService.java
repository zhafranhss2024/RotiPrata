package com.rotiprata.api.browsing.service;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.dto.GetHistoryDTO;
import java.time.Instant;
import java.util.List;

/**
 * Service interface for browsing-related operations.
 * Handles content search, lesson search, and user's search history management.
 */
public interface BrowsingService {

    /**
     * Handles search.
     */
    /**
     * Searches for content and lessons based on a query and optional filter.
     * If filter is "video", only video content is returned.
     * If filter is "lesson", only lessons are returned.
     * If filter is empty or null, both videos and lessons are returned.
     *
     * @param query       the search query string
     * @param filter      optional filter: "video" or "lesson"
     * @param accessToken user's access token for authorization
     * @return a list of ContentSearchDTO representing search results
     * @throws RuntimeException if the underlying content or lesson service fails
     */
    List<ContentSearchDTO> search(String query, String filter, String accessToken);

    /**
     * Handles search.
     */
    /**
     * Saves a search query to the user's search history.
     * If the query already exists, updates the timestamp instead of creating a duplicate.
     *
     * @param userId      the user's ID
     * @param query       the search query string
     * @param searchedAt  the timestamp of the search (optional, defaults to now if null)
     * @param accessToken user's access token for authorization
     * @throws RuntimeException if saving to the database fails
     */
    void saveHistory(String userId, String query, Instant searchedAt, String accessToken);

    /**
     * Fetches the history.
     */
    /**
     * Fetches the last 5 search history entries for a user, sorted by most recent first.
     *
     * @param userId      the user's ID
     * @param accessToken user's access token for authorization
     * @return a list of GetHistoryDTO representing recent search entries
     * @throws RuntimeException if fetching from the database fails
     */
    List<GetHistoryDTO> fetchHistory(String userId, String accessToken);

    /**
     * Deletes the history by id.
     */
    /**
     * Deletes a search history entry by its ID for a specific user.
     *
     * @param id          the ID of the search history entry to delete
     * @param userId      the user's ID
     * @param accessToken user's access token for authorization
     * @throws RuntimeException if deletion from the database fails
     */
    void deleteHistoryById(String id, String userId, String accessToken);
}

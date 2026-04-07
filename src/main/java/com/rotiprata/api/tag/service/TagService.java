package com.rotiprata.api.tag.service;

import java.util.List;

/**
 * Service interface for searching and fetching tags from the system.
 *
 * Provides functionality to query tags by partial text, normalize results,
 * and enforce a maximum limit of returned tags.
 */
public interface TagService {

    /**
     * Searches tags that match the given query string.
     *
     * - Trims and normalizes the input query.
     * - Supports case-insensitive partial matching (ilike).
     * - Returns up to a default limit of tags in ascending order.
     *
     * @param query the partial or full text to search for tags; may be null or blank
     * @return a list of unique tag strings, sorted alphabetically
     */
    List<String> searchTags(String query);
}
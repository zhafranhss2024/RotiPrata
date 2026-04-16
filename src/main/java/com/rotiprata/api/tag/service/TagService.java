package com.rotiprata.api.tag.service;

import java.util.List;

/**
 * Defines the tag search operations exposed to the API layer.
 */
public interface TagService {

    /**
     * Searches available tags for the provided query.
     */
    List<String> searchTags(String query);
}

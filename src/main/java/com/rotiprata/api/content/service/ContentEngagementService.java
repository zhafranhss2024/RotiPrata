package com.rotiprata.api.content.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defines the content engagement decoration operations exposed to the API layer.
 */
public interface ContentEngagementService {

    /**
     * Adds user engagement flags to content items.
     */
    List<Map<String, Object>> decorateItemsWithUserEngagement(
        List<Map<String, Object>> items,
        UUID userId,
        String accessToken
    );
}

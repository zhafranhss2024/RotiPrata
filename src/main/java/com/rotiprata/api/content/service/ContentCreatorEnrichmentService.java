package com.rotiprata.api.content.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for enriching content items with creator profile information.
 * Handles fetching and attaching creator profiles to content.
 */
public interface ContentCreatorEnrichmentService {

    /**
     * Enriches a list of content items with creator profiles.
     *
     * Each item that contains a "creator_id" will have an additional "creator" map
     * with profile information (user_id, display_name, avatar_url) attached.
     *
     * @param items list of content items to enrich
     * @return list of enriched content items
     * @throws RuntimeException if fetching creator profiles fails
     */
    List<Map<String, Object>> enrichWithCreatorProfiles(List<Map<String, Object>> items);
}
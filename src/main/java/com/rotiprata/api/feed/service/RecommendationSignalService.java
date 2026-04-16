package com.rotiprata.api.feed.service;

import java.util.UUID;

/**
 * Defines the recommendation signal loading operations exposed to the API layer.
 */
public interface RecommendationSignalService {

    /**
     * Loads recommendation signals for the current user.
     */
    RecommendationSignals loadSignals(UUID userId);
}

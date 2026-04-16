package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.response.FeedResponse;
import com.rotiprata.api.feed.response.RecommendationResponse;
import java.util.UUID;

/**
 * Defines the recommendation service operations exposed to the API layer.
 */
public interface RecommendationService {

    /**
     * Returns the feed recommendation surface.
     */
    FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit);

    /**
     * Returns the explore recommendation surface.
     */
    RecommendationResponse getRecommendations(UUID userId, String accessToken, Integer limit);
}

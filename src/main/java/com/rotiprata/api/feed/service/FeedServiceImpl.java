package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.response.FeedResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements the feed service workflows and persistence coordination used by the API layer.
 */
@Service
public class FeedServiceImpl implements FeedService {
    private final RecommendationService recommendationService;

    /**
     * Creates a feed service instance with its collaborators.
     */
    public FeedServiceImpl(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Returns the feed.
     */
    public FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit) {
        return recommendationService.getFeed(userId, accessToken, cursor, limit);
    }
}

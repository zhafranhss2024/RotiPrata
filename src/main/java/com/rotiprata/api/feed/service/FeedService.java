package com.rotiprata.api.feed.service;

import com.rotiprata.api.zdto.FeedResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FeedService {
    private final RecommendationService recommendationService;

    public FeedService(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    public FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit) {
        return recommendationService.getFeed(userId, accessToken, cursor, limit);
    }
}

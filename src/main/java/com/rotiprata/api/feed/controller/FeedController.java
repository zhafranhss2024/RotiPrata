package com.rotiprata.api.feed.controller;

import com.rotiprata.api.feed.service.FeedService;
import com.rotiprata.api.feed.service.RecommendationService;
import com.rotiprata.api.feed.response.FeedResponse;
import com.rotiprata.api.feed.response.RecommendationResponse;
import com.rotiprata.security.SecurityUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeedController {
    private final FeedService feedService;
    private final RecommendationService recommendationService;

    public FeedController(FeedService feedService, RecommendationService recommendationService) {
        this.feedService = feedService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/feed")
    public FeedResponse feed(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (jwt == null) {
            return new FeedResponse(java.util.List.of(), false, null);
        }
        return feedService.getFeed(SecurityUtils.getUserId(jwt), SecurityUtils.getAccessToken(), cursor, limit);
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommendations(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (jwt == null) {
            return new RecommendationResponse(java.util.List.of());
        }
        return recommendationService.getRecommendations(SecurityUtils.getUserId(jwt), SecurityUtils.getAccessToken(), limit);
    }
}

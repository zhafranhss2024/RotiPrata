package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.response.FeedResponse;
import java.util.UUID;

/**
 * Defines the feed service operations exposed to the API layer.
 */
public interface FeedService {

    /**
     * Returns a paged feed for the current user.
     */
    FeedResponse getFeed(UUID userId, String accessToken, String cursor, Integer limit);
}

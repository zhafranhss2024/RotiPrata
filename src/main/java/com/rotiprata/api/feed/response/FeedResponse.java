package com.rotiprata.api.feed.response;

import java.util.List;
import java.util.Map;

/**
 * Represents the feed response payload returned by the API layer.
 */
public record FeedResponse(List<Map<String, Object>> items, boolean hasMore, String nextCursor) {}

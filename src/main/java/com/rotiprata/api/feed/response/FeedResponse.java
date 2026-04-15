package com.rotiprata.api.feed.response;

import java.util.List;
import java.util.Map;

public record FeedResponse(List<Map<String, Object>> items, boolean hasMore, String nextCursor) {}

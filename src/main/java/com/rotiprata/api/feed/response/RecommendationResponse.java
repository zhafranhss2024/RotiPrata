package com.rotiprata.api.feed.response;

import java.util.List;
import java.util.Map;

/**
 * Represents the recommendation response payload returned by the API layer.
 */
public record RecommendationResponse(List<Map<String, Object>> items) {}

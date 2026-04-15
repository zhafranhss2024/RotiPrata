package com.rotiprata.api.feed.response;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(List<Map<String, Object>> items) {}

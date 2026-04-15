package com.rotiprata.api.zdto;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(List<Map<String, Object>> items) {}

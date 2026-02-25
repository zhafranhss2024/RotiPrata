package com.rotiprata.api.dto;

import java.util.List;
import java.util.Map;

public record AdminStepSaveRequest(
    Map<String, Object> lesson,
    List<Map<String, Object>> questions
) {}

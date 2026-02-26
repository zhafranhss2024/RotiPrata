package com.rotiprata.application.quiz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

abstract class AbstractLessonQuizQuestionGrader implements LessonQuizQuestionGrader {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }

    protected List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    protected String asString(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    protected Map<String, Object> parseJsonObject(String raw, String fieldName) {
        String text = asString(raw);
        if (text == null) {
            throw badRequest("Missing " + fieldName);
        }
        try {
            return OBJECT_MAPPER.readValue(text, MAP_TYPE);
        } catch (Exception ex) {
            throw badRequest("Invalid JSON in " + fieldName);
        }
    }

    protected Map<String, Object> resolveResponseMap(Map<String, Object> response, String key) {
        if (response == null) {
            return Map.of();
        }
        Object nested = response.get(key);
        if (nested == null) {
            return response;
        }
        return asObject(nested);
    }

    protected ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

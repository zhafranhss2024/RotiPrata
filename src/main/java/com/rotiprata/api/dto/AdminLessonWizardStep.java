package com.rotiprata.api.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum AdminLessonWizardStep {
    BASICS("basics"),
    CONTENT("content"),
    QUIZ_SETUP("quiz_setup"),
    QUIZ_BUILDER("quiz_builder"),
    REVIEW_PUBLISH("review_publish");

    private final String key;

    AdminLessonWizardStep(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static AdminLessonWizardStep fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing wizard step");
        }
        String normalized = key.trim().toLowerCase();
        for (AdminLessonWizardStep step : values()) {
            if (step.key.equals(normalized)) {
                return step;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown wizard step: " + key);
    }
}

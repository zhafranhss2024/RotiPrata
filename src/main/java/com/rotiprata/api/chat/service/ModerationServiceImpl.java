package com.rotiprata.api.chat.service;

import com.rotiprata.infrastructure.openai.OpenAiRestClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.rotiprata.api.exception.ModerationServiceException;

import java.util.List;
import java.util.Map;

/**
 * Implementation of ModerationService.
 * Uses OpenAI's moderation API to determine if text contains inappropriate content.
 */
@Service
public class ModerationServiceImpl implements ModerationService {

    private final OpenAiRestClient openAiRestClient;
    private final String moderationPath;

    // Constant
    private static final String MODEL_NAME = "omni-moderation-latest";

    /**
     * Creates a moderation service impl instance with its collaborators.
     */
    /**
     * Constructor for dependency injection.
     */
    public ModerationServiceImpl(
            OpenAiRestClient openAiRestClient,
            @Value("${spring.ai.openai.moderation.path}") String moderationPath
    ) {
        this.openAiRestClient = openAiRestClient;
        this.moderationPath = moderationPath;
    }

    /**
     * Checks whether flagged.
     */
    // Checks if the given text is flagged as inappropriate using the moderation API
    @Override
    public boolean isFlagged(String text) {
        try {
            Map<String, Object> body = Map.of(
                "model", MODEL_NAME,
                "input", text
            );

            Map<String, Object> response = openAiRestClient.post(
                moderationPath,
                body,
                new TypeReference<Map<String, Object>>() {}
            );

            Object resultsObj = response.get("results");
            if (!(resultsObj instanceof List<?> resultsList) || resultsList.isEmpty()) return false;

            Object firstResultObj = resultsList.get(0);
            if (!(firstResultObj instanceof Map<?, ?> firstResult)) return false;

            Object flagged = firstResult.get("flagged");
            return flagged instanceof Boolean b && b;

        } catch (Exception e) {
            throw new ModerationServiceException("Failed to check moderation", e);
        }
    }
}

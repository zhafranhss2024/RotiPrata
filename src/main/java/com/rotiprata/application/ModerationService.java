package com.rotiprata.application;

import com.rotiprata.infrastructure.openai.OpenAiRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

@Service
public class ModerationService {

    private final OpenAiRestClient openAiRestClient;
    private final String moderationPath;

    public ModerationService(
            OpenAiRestClient openAiRestClient,
            @Value("${spring.ai.openai.moderation.path}") String moderationPath
    ) {
        this.openAiRestClient = openAiRestClient;
        this.moderationPath = moderationPath; 
    }

    public boolean isFlagged(String text) {

        Map<String,Object> body = Map.of(
            "model", "omni-moderation-latest",
            "input", text
        );

        Map<String,Object> response = openAiRestClient.post(
            moderationPath, 
            body, 
            new TypeReference<Map<String,Object>>() {}
        );

        Object resultsObj = response.get("results");
        if (!(resultsObj instanceof List<?> resultsList) || resultsList.isEmpty()) return false;

        Object firstResultObj = resultsList.get(0);
        if (!(firstResultObj instanceof Map<?, ?> firstResult)) return false;

        Object flagged = firstResult.get("flagged");
        return flagged instanceof Boolean b && b;
    }
}
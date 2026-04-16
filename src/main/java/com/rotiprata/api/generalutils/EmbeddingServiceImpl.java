package com.rotiprata.api.generalutils;

import org.springframework.stereotype.Service;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Documents the embedding service type.
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;

    /**
     * Creates a embedding service impl instance with its collaborators.
     */
    public EmbeddingServiceImpl(OpenAiEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Handles generate embedding.
     */
    // Generates an embedding vector for the given text
    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text for embedding is required");
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Converts the value into pg vector.
     */
    // Converts a float array to a Postgres-compatible vector string
    @Override
    public String toPgVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector must not be null or empty");
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

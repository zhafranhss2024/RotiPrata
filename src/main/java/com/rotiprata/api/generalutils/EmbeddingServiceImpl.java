package com.rotiprata.api.generalutils;

import org.springframework.stereotype.Service;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

/**
 * Implementation of EmbeddingService.
 * Uses OpenAI embedding model to generate embeddings and formats them for Postgres.
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final OpenAiEmbeddingModel embeddingModel;

    /**
     * Constructor for dependency injection.
     */
    public EmbeddingServiceImpl(OpenAiEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // ================= GENERATE EMBEDDING =================

    @Override
    public float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }

    // ================= CONVERT TO POSTGRES VECTOR =================

    @Override
    public String toPgVector(float[] vector) {
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
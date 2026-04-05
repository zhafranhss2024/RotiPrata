package com.rotiprata.api.generalutils;

/**
 * Service interface for generating and formatting embeddings.
 * Provides methods to generate embeddings for text and convert them into Postgres vector format.
 */
public interface EmbeddingService {

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text the input text
     * @return a float array representing the embedding
     */
    float[] generateEmbedding(String text);

    /**
     * Converts a float array embedding into Postgres-compatible vector string format.
     *
     * @param vector the embedding vector
     * @return string in the format "[v1,v2,v3,...]"
     */
    String toPgVector(float[] vector);
}
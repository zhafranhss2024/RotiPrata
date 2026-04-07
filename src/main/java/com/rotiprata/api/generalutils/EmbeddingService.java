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
     * @throws IllegalArgumentException if the input text is null or blank
     * @throws RuntimeException if the embedding generation fails
     */
    float[] generateEmbedding(String text);

    /**
     * Converts a float array embedding into Postgres-compatible vector string format.
     *
     * @param vector the embedding vector
     * @return string in the format "[v1,v2,v3,...]"
     * @throws IllegalArgumentException if the input vector is null or empty
     */
    String toPgVector(float[] vector);
}
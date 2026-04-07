package com.rotiprata.api.generalutils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingServiceImpl tests")
class EmbeddingServiceImplTest {

    @Mock
    private OpenAiEmbeddingModel embeddingModel;

    private EmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingServiceImpl(embeddingModel);
    }

    // Verifies embeddings are delegated to OpenAI model and returned unchanged.
    @Test
    void generateEmbedding_ShouldReturnModelEmbedding_WhenTextProvided() {
        // arrange
        String text = "What is roti prata?";
        float[] expected = new float[] {0.11f, -0.22f, 0.33f};
        when(embeddingModel.embed(text)).thenReturn(expected);

        // act
        float[] result = service.generateEmbedding(text);

        // assert
        assertArrayEquals(expected, result);

        // verify
        verify(embeddingModel, times(1)).embed(text);
    }

    // Verifies vectors are formatted as comma-separated values wrapped in brackets.
    @Test
    void toPgVector_ShouldFormatBracketedCommaSeparatedValues_WhenVectorHasMultipleElements() {
        // arrange
        float[] vector = new float[] {1.5f, -2.0f, 3.25f};

        // act
        String result = service.toPgVector(vector);

        // assert
        assertEquals("[1.5,-2.0,3.25]", result);

        // verify
        assertEquals('[', result.charAt(0));
        assertEquals(']', result.charAt(result.length() - 1));
    }

    // Verifies that generateEmbedding throws ResponseStatusException when text is null.
    @Test
    void generateEmbedding_ShouldThrowBadRequest_WhenTextIsNull() {
        // arrange
        String text = null;

        // act & assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateEmbedding(text));

        // assert
        assertEquals("Text for embedding is required", ex.getReason());
        assertEquals(400, ex.getStatusCode().value());

        // verify
        // nothing additional to verify for exception
    }

    // Verifies that generateEmbedding throws ResponseStatusException when text is blank.
    @Test
    void generateEmbedding_ShouldThrowBadRequest_WhenTextIsBlank() {
        // arrange
        String text = "   ";

        // act & assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generateEmbedding(text));

        // assert
        assertEquals("Text for embedding is required", ex.getReason());
        assertEquals(400, ex.getStatusCode().value());

        // verify
        // nothing additional to verify for exception
    }

    // Verifies that generateEmbedding throws RuntimeException when the embedding model fails.
    @Test
    void generateEmbedding_ShouldThrowRuntimeException_WhenModelFails() {
        // arrange
        String text = "fail test";
        when(embeddingModel.embed(text)).thenThrow(new RuntimeException("model error"));

        // act & assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateEmbedding(text));

        // assert
        assertTrue(ex.getMessage().contains("Failed to generate embedding"));
        assertNotNull(ex.getCause());
        assertEquals("model error", ex.getCause().getMessage());

        // verify
        verify(embeddingModel, times(1)).embed(text);
    }

    // Verifies that toPgVector throws IllegalArgumentException when vector is null.
    @Test
    void toPgVector_ShouldThrowIllegalArgumentException_WhenVectorIsNull() {
        // arrange
        float[] vector = null;

        // act & assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.toPgVector(vector));

        // assert
        assertEquals("Vector must not be null or empty", ex.getMessage());

        // verify
        // nothing additional to verify for exception
    }

    // Verifies that toPgVector throws IllegalArgumentException when vector is empty.
    @Test
    void toPgVector_ShouldThrowIllegalArgumentException_WhenVectorIsEmpty() {
        // arrange
        float[] vector = new float[0];

        // act & assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.toPgVector(vector));

        // assert
        assertEquals("Vector must not be null or empty", ex.getMessage());
    }
}
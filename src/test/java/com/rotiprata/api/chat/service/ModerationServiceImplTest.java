package com.rotiprata.api.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.exception.ModerationServiceException;
import com.rotiprata.infrastructure.openai.OpenAiRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers moderation service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModerationServiceImpl tests")
class ModerationServiceImplTest {

    @Mock
    private OpenAiRestClient openAiRestClient;

    private ModerationServiceImpl service;

    private static final String MODERATION_PATH = "/v1/moderations";

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(openAiRestClient, MODERATION_PATH);
    }

    // Returns true when first moderation result has flagged=true
    @Test
    @SuppressWarnings("unchecked")
    void isFlagged_ShouldReturnTrue_WhenFirstResultIsFlaggedTrue() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("results", List.of(Map.of("flagged", true))));

        // Act
        boolean result = service.isFlagged("unsafe text");

        // Assert
        assertTrue(result);

        // Verify request body
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(openAiRestClient, times(1))
            .post(
                eq(MODERATION_PATH),
                bodyCaptor.capture(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
            );

        Map<String, Object> sentBody = bodyCaptor.getValue();
        assertEquals("omni-moderation-latest", sentBody.get("model"));
        assertEquals("unsafe text", sentBody.get("input"));
    }

    /**
     * Verifies that is flagged should return false when results field is missing.
     */
    // Returns false when the API response does not contain 'results' field
    @Test
    void isFlagged_ShouldReturnFalse_WhenResultsFieldIsMissing() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("id", "mod_123"));

        // Act
        boolean result = service.isFlagged("text");

        // Assert
        assertFalse(result);
    }

    /**
     * Verifies that is flagged should return false when results list is empty.
     */
    // Returns false when the 'results' list is empty
    @Test
    void isFlagged_ShouldReturnFalse_WhenResultsListIsEmpty() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("results", List.of()));

        // Act
        boolean result = service.isFlagged("text");

        // Assert
        assertFalse(result);
    }

    /**
     * Verifies that is flagged should return false when first result is not map.
     */
    // Returns false when first result is not a Map object
    @Test
    void isFlagged_ShouldReturnFalse_WhenFirstResultIsNotMap() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("results", List.of("invalid")));

        // Act
        boolean result = service.isFlagged("text");

        // Assert
        assertFalse(result);
    }

    /**
     * Verifies that is flagged should return false when flagged is false.
     */
    // Returns false when the 'flagged' field is false
    @Test
    void isFlagged_ShouldReturnFalse_WhenFlaggedIsFalse() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("results", List.of(Map.of("flagged", false))));

        // Act
        boolean result = service.isFlagged("text");

        // Assert
        assertFalse(result);
    }

    /**
     * Verifies that is flagged should return false when flagged is not boolean.
     */
    // Returns false when the 'flagged' field is not a boolean
    @Test
    void isFlagged_ShouldReturnFalse_WhenFlaggedIsNotBoolean() {
        // Arrange
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenReturn(Map.of("results", List.of(Map.of("flagged", "true"))));

        // Act
        boolean result = service.isFlagged("text");

        // Assert
        assertFalse(result);
    }

    /**
     * Verifies that is flagged should throw moderation service exception when client throws exception.
     */
    // Throws ModerationServiceException when the API client throws an exception
    @Test
    void isFlagged_ShouldThrowModerationServiceException_WhenClientThrowsException() {
        // Arrange
        RuntimeException rootCause = new RuntimeException("network error");
        when(openAiRestClient.post(
                eq(MODERATION_PATH),
                any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any()))
            .thenThrow(rootCause);

        // Act & Assert
        ModerationServiceException exception =
            assertThrows(ModerationServiceException.class, () -> service.isFlagged("text"));

        assertEquals("Failed to check moderation", exception.getMessage());
        assertNotNull(exception.getCause());
        assertSame(rootCause, exception.getCause());
    }
}

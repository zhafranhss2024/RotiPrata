package com.rotiprata.api.feed.controller;

import com.rotiprata.api.feed.service.FeedService;
import com.rotiprata.api.feed.service.RecommendationService;
import com.rotiprata.api.feed.response.FeedResponse;
import com.rotiprata.api.feed.response.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    private FeedService feedService;

    @Mock
    private RecommendationService recommendationService;

    private FeedController feedController;

    @BeforeEach
    void setUp() {
        feedController = new FeedController(feedService, recommendationService);
    }

    /** Verifies the controller returns the empty feed contract when authentication is missing. */
    @Test
    void feed_ShouldReturnEmptyResponse_WhenJwtIsNull() {
        // arrange

        // act
        FeedResponse response = feedController.feed(null, null, null);

        // assert
        assertTrue(response.items().isEmpty());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());

        // verify
        verifyNoInteractions(feedService);
    }

    /** Verifies the controller returns the empty recommendation contract when authentication is missing. */
    @Test
    void recommendations_ShouldReturnEmptyResponse_WhenJwtIsNull() {
        // arrange

        // act
        RecommendationResponse response = feedController.recommendations(null, null);

        // assert
        assertTrue(response.items().isEmpty());

        // verify
        verifyNoInteractions(recommendationService);
    }
}

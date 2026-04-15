package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.response.FeedResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private RecommendationService recommendationService;

    private FeedService feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(recommendationService);
    }

    /** Verifies the feed facade delegates directly to the recommendation service. */
    @Test
    void getFeed_ShouldDelegateToRecommendationService_WhenFeedIsRequested() {
        // arrange
        UUID userId = UUID.randomUUID();
        FeedResponse expected = new FeedResponse(List.of(Map.of("id", "video-1")), true, "cursor-1");
        when(recommendationService.getFeed(userId, "token", "cursor-0", 10)).thenReturn(expected);

        // act
        FeedResponse actual = feedService.getFeed(userId, "token", "cursor-0", 10);

        // assert
        assertSame(expected, actual);

        // verify
        verify(recommendationService).getFeed(userId, "token", "cursor-0", 10);
    }
}

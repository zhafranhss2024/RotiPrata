package com.rotiprata.api.feed.service;

import com.rotiprata.api.zdto.FeedResponse;
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

    @Test
    void getFeed_shouldDelegateToRecommendationService() {
        UUID userId = UUID.randomUUID();
        FeedResponse expected = new FeedResponse(List.of(Map.of("id", "video-1")), true, "cursor-1");

        when(recommendationService.getFeed(userId, "token", "cursor-0", 10)).thenReturn(expected);

        FeedResponse actual = feedService.getFeed(userId, "token", "cursor-0", 10);

        assertSame(expected, actual);
        verify(recommendationService).getFeed(userId, "token", "cursor-0", 10);
    }
}

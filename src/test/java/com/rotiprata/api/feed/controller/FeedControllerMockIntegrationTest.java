package com.rotiprata.api.feed.controller;

import com.rotiprata.api.feed.service.FeedService;
import com.rotiprata.api.feed.service.RecommendationService;
import com.rotiprata.api.zdto.FeedResponse;
import com.rotiprata.api.zdto.RecommendationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
@AutoConfigureMockMvc
@DisplayName("FeedController integration tests")
class FeedControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedController feedController;

    @MockBean
    private FeedService feedService;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void feed_shouldReturnPagedRecommendations() throws Exception {
        UUID userId = UUID.randomUUID();
        when(feedService.getFeed(any(), any(), any(), anyInt()))
            .thenReturn(new FeedResponse(
                List.of(Map.of("id", "video-1", "title", "Video One")),
                true,
                "cursor-1"
            ));

        mockMvc.perform(get("/api/feed")
                .queryParam("limit", "1")
                .queryParam("cursor", "cursor-0")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).tokenValue("mocked-jwt-token"))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.items[0].id").value("video-1"))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.nextCursor").value("cursor-1"));
    }

    @Test
    void recommendations_shouldReturnExploreItems() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.getRecommendations(any(), any(), any()))
            .thenReturn(new RecommendationResponse(
                List.of(Map.of("id", "video-2", "title", "Video Two"))
            ));

        mockMvc.perform(get("/api/recommendations")
                .queryParam("limit", "3")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).tokenValue("mocked-jwt-token"))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.items[0].id").value("video-2"));
    }

    @Test
    void feed_shouldReturnEmptyPayloadWhenJwtMissing() throws Exception {
        FeedResponse response = feedController.feed(null, null, null);

        org.junit.jupiter.api.Assertions.assertTrue(response.items().isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(response.hasMore());
        org.junit.jupiter.api.Assertions.assertNull(response.nextCursor());
        verifyNoInteractions(feedService);
    }

    @Test
    void recommendations_shouldReturnEmptyPayloadWhenJwtMissing() throws Exception {
        RecommendationResponse response = feedController.recommendations(null, null);

        org.junit.jupiter.api.Assertions.assertTrue(response.items().isEmpty());
        verifyNoInteractions(recommendationService);
    }
}

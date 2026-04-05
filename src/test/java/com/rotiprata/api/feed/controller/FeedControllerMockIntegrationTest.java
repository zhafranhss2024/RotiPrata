package com.rotiprata.api.feed.controller;

import com.rotiprata.api.feed.service.FeedService;
import com.rotiprata.api.feed.service.RecommendationService;
import com.rotiprata.api.zdto.FeedResponse;
import com.rotiprata.api.zdto.RecommendationResponse;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("FeedController integration tests")
class FeedControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedService feedService;

    @MockBean
    private RecommendationService recommendationService;

    private MockMvcRequestSpecification auth;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        auth = given()
            .auth().with(jwt().jwt(j -> j
                .subject(UUID.randomUUID().toString())
                .tokenValue("mocked-jwt-token")
            ));
    }

    @Test
    void feed_shouldReturnPagedRecommendations() {
        when(feedService.getFeed(any(), any(), any(), anyInt()))
            .thenReturn(new FeedResponse(
                List.of(Map.of("id", "video-1", "title", "Video One")),
                true,
                "cursor-1"
            ));

        auth
            .queryParam("limit", 1)
        .when()
            .get("/api/feed")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("items[0].id", equalTo("video-1"))
            .body("hasMore", equalTo(true))
            .body("nextCursor", equalTo("cursor-1"));
    }

    @Test
    void recommendations_shouldReturnExploreItems() {
        when(recommendationService.getRecommendations(any(), any(), any()))
            .thenReturn(new RecommendationResponse(
                List.of(Map.of("id", "video-2", "title", "Video Two"))
            ));

        auth
        .when()
            .get("/api/recommendations")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("items[0].id", equalTo("video-2"));
    }
}

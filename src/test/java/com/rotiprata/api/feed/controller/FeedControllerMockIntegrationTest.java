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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(FeedController.class)
@AutoConfigureMockMvc
@DisplayName("FeedController mock integration tests")
class FeedControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedService feedService;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID userId;
    private MockMvcRequestSpecification authenticatedRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        RestAssuredMockMvc.mockMvc(mockMvc);

        // The controller reads the JWT subject and token from Spring Security's request context.
        authenticatedRequest = given().auth().with(jwt().jwt(jwt -> jwt
            .subject(userId.toString())
            .tokenValue("mocked-jwt-token")
        ));
    }

    /** Verifies the feed endpoint returns the paged recommendation payload for an authenticated user. */
    @Test
    void feed_ShouldReturnPagedRecommendations_WhenUserIsAuthenticated() {
        // arrange
        when(feedService.getFeed(userId, "mocked-jwt-token", "cursor-0", 1))
            .thenReturn(new FeedResponse(
                List.of(Map.of("id", "video-1", "title", "Video One")),
                true,
                "cursor-1"
            ));

        // act
        authenticatedRequest
            .queryParam("limit", "1")
            .queryParam("cursor", "cursor-0")
        .when()
            .get("/api/feed")
        .then()
            // assert
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("items[0].id", equalTo("video-1"))
            .body("hasMore", equalTo(true))
            .body("nextCursor", equalTo("cursor-1"));

        // verify
        verify(feedService).getFeed(userId, "mocked-jwt-token", "cursor-0", 1);
    }

    /** Verifies the explore endpoint returns recommendation items for an authenticated user. */
    @Test
    void recommendations_ShouldReturnExploreItems_WhenUserIsAuthenticated() {
        // arrange
        when(recommendationService.getRecommendations(userId, "mocked-jwt-token", 3))
            .thenReturn(new RecommendationResponse(
                List.of(Map.of("id", "video-2", "title", "Video Two"))
            ));

        // act
        authenticatedRequest
            .queryParam("limit", "3")
        .when()
            .get("/api/recommendations")
        .then()
            // assert
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("items[0].id", equalTo("video-2"));

        // verify
        verify(recommendationService).getRecommendations(userId, "mocked-jwt-token", 3);
    }
}

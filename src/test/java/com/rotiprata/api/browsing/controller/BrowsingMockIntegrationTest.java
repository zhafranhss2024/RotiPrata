package com.rotiprata.api.browsing.controller;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.service.BrowsingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import io.restassured.http.ContentType;

import java.util.List;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("BrowsingController Mock Integration Tests")
class BrowsingControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrowsingService browsingService;

    private MockMvcRequestSpecification userAuth;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        // Inject mock JWT for authentication
        userAuth = given().auth().with(jwt().jwt(j -> j
                .subject("admin_id_123")
                .tokenValue("mocked-jwt-token")
        ));
    }

    // ------------------- Success Cases -------------------

    /** Test search endpoint with empty filter returns all results */
    @Test
    void search_ShouldReturnAllResults_WhenFilterEmpty() {
        List<ContentSearchDTO> mockResults = List.of(
                new ContentSearchDTO("1", "video", "Video Title", "Video Desc", "Video Snippet"),
                new ContentSearchDTO("2", "lesson", "Lesson Title", "Lesson Desc", "Lesson Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "")
        .when()
            .get("/api/search")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(2))
            .body("[0].content_type", equalTo("video"))
            .body("[1].content_type", equalTo("lesson"));
    }

    /** Test search endpoint with filter=video returns only videos */
    @Test
    void search_ShouldReturnOnlyVideos_WhenFilterVideo() {
        List<ContentSearchDTO> mockResults = List.of(
                new ContentSearchDTO("1", "video", "Video Title", "Video Desc", "Video Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "video")
        .when()
            .get("/api/search")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].content_type", equalTo("video"));
    }

    /** Test search endpoint with filter=lesson returns only lessons */
    @Test
    void search_ShouldReturnOnlyLessons_WhenFilterLesson() {
        List<ContentSearchDTO> mockResults = List.of(
                new ContentSearchDTO("2", "lesson", "Lesson Title", "Lesson Desc", "Lesson Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString()))
                .thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "lesson")
        .when()
            .get("/api/search")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].content_type", equalTo("lesson"));
    }

    // ------------------- Validation / Edge Cases -------------------

    /** Test search with null query returns empty list */
    @Test
    void search_ShouldReturnEmptyList_WhenQueryNull() {
        when(browsingService.search(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        userAuth
            .queryParam("query", (String) null)
        .when()
            .get("/api/search")
        .then()
            .status(HttpStatus.OK)
            .body("$", hasSize(0));
    }
}
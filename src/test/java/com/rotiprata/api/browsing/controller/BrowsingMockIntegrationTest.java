package com.rotiprata.api.browsing.controller;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.service.BrowsingService;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import java.util.List;
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
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Covers browsing scenarios and regression behavior for the current branch changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("BrowsingController Mock Integration Tests")
class BrowsingControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BrowsingService browsingService;

    private MockMvcRequestSpecification userAuth;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        userAuth = given().auth().with(jwt().jwt(j -> j
            .subject("admin_id_123")
            .tokenValue("mocked-jwt-token")
        ));
    }

    /**
     * Verifies that search should return all results when filter empty.
     */
    @Test
    void search_ShouldReturnAllResults_WhenFilterEmpty() {
        List<ContentSearchDTO> mockResults = List.of(
            new ContentSearchDTO("1", "video", "Video Title", "Video Desc", "Video Snippet"),
            new ContentSearchDTO("2", "lesson", "Lesson Title", "Lesson Desc", "Lesson Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString())).thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "")
        .when()
            .get("/api/search-results")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(2))
            .body("[0].content_type", equalTo("video"))
            .body("[1].content_type", equalTo("lesson"));
    }

    /**
     * Verifies that search should return only videos when filter video.
     */
    @Test
    void search_ShouldReturnOnlyVideos_WhenFilterVideo() {
        List<ContentSearchDTO> mockResults = List.of(
            new ContentSearchDTO("1", "video", "Video Title", "Video Desc", "Video Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString())).thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "video")
        .when()
            .get("/api/search-results")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].content_type", equalTo("video"));
    }

    /**
     * Verifies that search should return only lessons when filter lesson.
     */
    @Test
    void search_ShouldReturnOnlyLessons_WhenFilterLesson() {
        List<ContentSearchDTO> mockResults = List.of(
            new ContentSearchDTO("2", "lesson", "Lesson Title", "Lesson Desc", "Lesson Snippet")
        );
        when(browsingService.search(anyString(), anyString(), anyString())).thenReturn(mockResults);

        userAuth
            .queryParam("query", "test")
            .queryParam("filter", "lesson")
        .when()
            .get("/api/search-results")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].content_type", equalTo("lesson"));
    }

    /**
     * Verifies that search should return empty list when query null.
     */
    @Test
    void search_ShouldReturnEmptyList_WhenQueryNull() {
        when(browsingService.search(anyString(), anyString(), anyString())).thenReturn(List.of());

        userAuth
            .queryParam("query", (String) null)
        .when()
            .get("/api/search-results")
        .then()
            .status(HttpStatus.OK)
            .body("$", hasSize(0));
    }

    /**
     * Verifies that legacy search alias should still work.
     */
    @Test
    void legacySearchAlias_ShouldStillWork() {
        when(browsingService.search(anyString(), any(), anyString()))
            .thenReturn(List.of(new ContentSearchDTO("1", "video", "Video Title", "Video Desc", "Video Snippet")));

        userAuth
            .queryParam("query", "test")
        .when()
            .get("/api/search")
        .then()
            .status(HttpStatus.OK)
            .body("$", hasSize(1))
            .body("[0].content_type", equalTo("video"));
    }
}

package com.rotiprata.api.admin.controller;

import com.rotiprata.api.admin.service.AdminAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Admin Analytics Mock Controller Tests")
class AdminAnalyticsMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnalyticsService analyticsService;

    private MockMvcRequestSpecification adminAuth;

    @BeforeEach
    void setUp() {
        // Configure RestAssuredMockMvc to use MockMvc and inject a mock JWT for authorization
        RestAssuredMockMvc.mockMvc(mockMvc);

        adminAuth = given()
            .auth().with(jwt().jwt(j -> j
                .subject("admin_id_123")
                .tokenValue("mocked-jwt-token")
            ));
    }

    // ------------------- Success Cases -------------------

    /** Test fetching flagged content for a valid month/year */ 
    @Test
    void getFlaggedContentByMonthAndYear_ShouldReturnList_WhenValid() {
        when(analyticsService.getFlaggedContentByMonthAndYear(anyString(), anyString(), anyString()))
                .thenReturn(List.of(Map.of("date", "2026-04-01", "count", 5)));

        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("[0].date", equalTo("2026-04-01"))
            .body("[0].count", equalTo(5));
    }

    /** Test fetching average review time for a valid month/year */
    @Test
    void getAvgReviewTime_ShouldReturnMap_WhenValid() {
        when(analyticsService.getAverageReviewTimeByMonthAndYear(anyString(), anyString(), anyString()))
                .thenReturn(120.0);

        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/avg-review-time")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("avgReviewTime", equalTo(120f));
    }

    /** Test fetching top users who flagged content */
    @Test
    void getTopFlagUsers_ShouldReturnList_WhenValid() {
        when(analyticsService.getTopFlagUsers(anyString(), anyString()))
                .thenReturn(List.of(Map.of("userId", "123", "flags", 10)));

        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/top-flag-users")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("[0].userId", equalTo("123"))
            .body("[0].flags", equalTo(10));
    }

    /** Test fetching top flagged content */
    @Test
    void getTopFlagContents_ShouldReturnList_WhenValid() {
        when(analyticsService.getTopFlagContents(anyString(), anyString()))
                .thenReturn(List.of(Map.of("contentId", "abc", "flags", 7)));

        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/top-flag-contents")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("[0].contentId", equalTo("abc"))
            .body("[0].flags", equalTo(7));
    }

    /** Test fetching audit logs for a valid month/year */
    @Test
    void getAuditLogs_ShouldReturnList_WhenValid() {
        when(analyticsService.getAuditLogs(anyString(), anyString()))
                .thenReturn(List.of(Map.of("logId", "log1", "action", "UPDATE")));

        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/audit-logs")
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("[0].logId", equalTo("log1"))
            .body("[0].action", equalTo("UPDATE"));
    }

    // ------------------- Validation / Bad Requests -------------------

    /** Test that requesting a future month returns BAD_REQUEST */
    @Test
    void getFlaggedContentByMonthAndYear_ShouldReturnBadRequest_WhenFutureMonth() {
        adminAuth
            .queryParam("month", "12")
            .queryParam("year", "2999")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Cannot request future month"));
    }

    /** Test that non-numeric month/year returns validation error */
    @Test
    void getFlaggedContentByMonthAndYear_ShouldReturnBadRequest_WhenNonNumericMonthYear() {
        adminAuth
            .queryParam("month", "ab")
            .queryParam("year", "xyz")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"));
    }

    /** Test average review time request with future month returns BAD_REQUEST */
    @Test
    void getAvgReviewTime_ShouldReturnBadRequest_WhenFutureMonth() {
        adminAuth
            .queryParam("month", "12")
            .queryParam("year", "2999")
        .when()
            .get("/api/admin/analytics/avg-review-time")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Cannot request future month"));
    }

}
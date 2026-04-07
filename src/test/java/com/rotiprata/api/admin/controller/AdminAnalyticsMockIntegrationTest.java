package com.rotiprata.api.admin.controller;

import com.rotiprata.api.admin.service.AdminAnalyticsService;
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
import org.springframework.web.server.ResponseStatusException;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("AdminAnalytics Mock Integration Tests")
class AdminAnalyticsMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnalyticsService analyticsService;

    private MockMvcRequestSpecification adminAuth;
    private MockMvcRequestSpecification nonAdminAuth;

    @BeforeEach
    void setUp() {
        // Configure RestAssuredMockMvc to use MockMvc and inject mock JWTs for authorization.
        RestAssuredMockMvc.mockMvc(mockMvc);

        adminAuth = given()
            .auth().with(jwt().jwt(j -> j
                .subject(UUID.randomUUID().toString())
                .tokenValue("mocked-jwt-token")
            ));

        nonAdminAuth = given()
            .auth().with(jwt().jwt(j -> j
                .subject(UUID.randomUUID().toString())
                .tokenValue("mocked-non-admin-token")
            ));
    }

    // ------------------- Success Cases -------------------

    /** Test fetching flagged content for a valid month/year. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnList_whenValid() {
        // Arrange
        when(analyticsService.getFlaggedContentByMonthAndYear(any(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(Map.of("date", "2026-04-01", "count", 5L)));

        // Act / Assert
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

    /** Test fetching average review time for a valid month/year. */
    @Test
    void getAvgReviewTime_shouldReturnMap_whenValid() {
        // Arrange
        when(analyticsService.getAverageReviewTimeByMonthAndYear(any(), anyString(), anyString(), anyString()))
            .thenReturn(120.0);

        // Act / Assert
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

    /** Test fetching top users who flagged content. */
    @Test
    void getTopFlagUsers_shouldReturnList_whenValid() {
        // Arrange
        when(analyticsService.getTopFlagUsers(any(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(Map.of("userId", "123", "flags", 10)));

        // Act / Assert
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

    /** Test fetching top flagged content. */
    @Test
    void getTopFlagContents_shouldReturnList_whenValid() {
        // Arrange
        when(analyticsService.getTopFlagContents(any(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(Map.of("contentId", "abc", "flags", 7)));

        // Act / Assert
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

    /** Test fetching audit logs for a valid month/year. */
    @Test
    void getAuditLogs_shouldReturnList_whenValid() {
        // Arrange
        when(analyticsService.getAuditLogs(any(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(Map.of("logId", "log1", "action", "UPDATE")));

        // Act / Assert
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

    // ------------------- Authorization / Validation -------------------

    /** Test that non-admin access is rejected when the service enforces role checks. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnForbidden_whenServiceRejectsUser() {
        // Arrange
        when(analyticsService.getFlaggedContentByMonthAndYear(any(), anyString(), anyString(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required"));

        // Act / Assert
        nonAdminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.FORBIDDEN);
    }

    /** Test that requesting a future month returns BAD_REQUEST. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenFutureMonth() {
        // Act / Assert
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

    /** Test that non-numeric month/year returns a validation error. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenNonNumericMonthYear() {
        // Act / Assert
        adminAuth
            .queryParam("month", "ab")
            .queryParam("year", "xyz")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"));
    }

    /** Test that out-of-range month values return BAD_REQUEST. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenMonthIsOutOfRange() {
        // Act / Assert
        adminAuth
            .queryParam("month", "13")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Month must be between 1 and 12"));
    }

    /** Test that non-positive year values return BAD_REQUEST. */
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenYearIsNonPositive() {
        // Act / Assert
        adminAuth
            .queryParam("month", "04")
            .queryParam("year", "0")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Year must be positive"));
    }

    /** Test average review time request with future month returns BAD_REQUEST. */
    @Test
    void getAvgReviewTime_shouldReturnBadRequest_whenFutureMonth() {
        // Act / Assert
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

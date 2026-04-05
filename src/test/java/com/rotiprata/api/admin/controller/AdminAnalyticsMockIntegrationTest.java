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
@DisplayName("AdminAnalyticsController integration tests")
class AdminAnalyticsMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnalyticsService analyticsService;

    private MockMvcRequestSpecification adminAuth;
    private MockMvcRequestSpecification nonAdminAuth;

    @BeforeEach
    void setUp() {
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

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnList_whenValid() {
        when(analyticsService.getFlaggedContentByMonthAndYear(any(), anyString(), anyString(), anyString()))
            .thenReturn(List.of(Map.of("date", "2026-04-01", "count", 5L)));

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

    @Test
    void getAvgReviewTime_shouldReturnMap_whenValid() {
        when(analyticsService.getAverageReviewTimeByMonthAndYear(any(), anyString(), anyString(), anyString()))
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

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnForbidden_whenServiceRejectsUser() {
        when(analyticsService.getFlaggedContentByMonthAndYear(any(), anyString(), anyString(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required"));

        nonAdminAuth
            .queryParam("month", "04")
            .queryParam("year", "2026")
        .when()
            .get("/api/admin/analytics/flags")
        .then()
            .status(HttpStatus.FORBIDDEN);
    }

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenFutureMonth() {
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

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenMonthIsOutOfRange() {
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

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnBadRequest_whenYearIsNonPositive() {
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
}

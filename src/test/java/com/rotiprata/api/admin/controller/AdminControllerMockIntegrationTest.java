package com.rotiprata.api.admin.controller;

import com.rotiprata.api.admin.service.AdminService;
import com.rotiprata.api.content.service.ContentQuizService;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("AdminController Mock Integration Tests")
class AdminControllerMockIntegrationTest {

    private static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FLAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String ACCESS_TOKEN = "admin-mock-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private ContentQuizService contentQuizService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private MockMvcRequestSpecification auth;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        auth = given()
            .auth().with(jwt().jwt(j -> j
                .subject(ADMIN_ID.toString())
                .tokenValue(ACCESS_TOKEN)
            ));
    }

    @Test
    void reviewContent_ShouldApprove_WhenStatusApproved() {
        auth
            .contentType(ContentType.JSON)
            .body("""
                {"status":"approved"}
                """)
        .when()
            .put("/api/admin/content/{contentId}/review", CONTENT_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        verify(adminService).approveContent(ADMIN_ID, CONTENT_ID, ACCESS_TOKEN);
    }

    @Test
    void reviewContent_ShouldReject_WhenStatusRejected() {
        auth
            .contentType(ContentType.JSON)
            .body("""
                {"status":"rejected","feedback":"Needs revision"}
                """)
        .when()
            .put("/api/admin/content/{contentId}/review", CONTENT_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        verify(adminService).rejectContent(ADMIN_ID, CONTENT_ID, "Needs revision", ACCESS_TOKEN);
    }

    @Test
    void resolveFlag_ShouldResolve_WhenStatusResolved() {
        auth
            .contentType(ContentType.JSON)
            .body("""
                {"status":"resolved"}
                """)
        .when()
            .put("/api/admin/flags/{flagId}/resolution", FLAG_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        verify(adminService).resolveFlag(ADMIN_ID, FLAG_ID, ACCESS_TOKEN);
    }

    @Test
    void resolveFlag_ShouldTakeDown_WhenStatusTakenDown() {
        auth
            .contentType(ContentType.JSON)
            .body("""
                {"status":"taken_down","feedback":"Policy violation"}
                """)
        .when()
            .put("/api/admin/flags/{flagId}/resolution", FLAG_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        verify(adminService).takeDownFlag(ADMIN_ID, FLAG_ID, "Policy violation", ACCESS_TOKEN);
    }

    @Test
    void legacyModerationAliases_ShouldStillWork() {
        auth
        .when()
            .put("/api/admin/content/{contentId}/approve", CONTENT_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        auth
        .when()
            .put("/api/admin/flags/{flagId}/resolve", FLAG_ID.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        verify(adminService).approveContent(ADMIN_ID, CONTENT_ID, ACCESS_TOKEN);
        verify(adminService).resolveFlag(ADMIN_ID, FLAG_ID, ACCESS_TOKEN);
    }
}

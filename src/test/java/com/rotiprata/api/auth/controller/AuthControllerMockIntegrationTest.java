package com.rotiprata.api.auth.controller;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import com.rotiprata.api.auth.dto.AuthSessionResponse;
import com.rotiprata.api.auth.service.AuthService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.api.auth.request.ForgotPasswordRequest;
import com.rotiprata.api.auth.request.LoginRequest;
import com.rotiprata.api.auth.response.LoginStreakTouchResponse;
import com.rotiprata.api.auth.request.RegisterRequest;
import com.rotiprata.api.auth.request.ResetPasswordRequest;
import com.rotiprata.application.LoginStreakService;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("AuthController Mock Integration Tests")
class AuthControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginStreakService loginStreakService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private MockMvcRequestSpecification authenticated;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        authenticated = given()
            .auth().with(jwt().jwt(j -> j
                .subject("11111111-1111-1111-1111-111111111111")
                .tokenValue("mocked-access-token")
            ));
    }

    /** Verifies login delegates to authService and returns the auth session payload. */
    @Test
    void login_ShouldReturnAuthSession_WhenCredentialsAreValid() {
        //arrange
        AuthSessionResponse response = new AuthSessionResponse(
            "access-token",
            "refresh-token",
            "Bearer",
            3600L,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "user@example.com",
            false,
            null
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        //act
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"email\": \"user@example.com\",
                  \"password\": \"StrongPass123!\"
                }
                """)
        .when()
            .post("/api/auth/sessions")
        //assert
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("accessToken", equalTo("access-token"))
            .body("refreshToken", equalTo("refresh-token"));

        //verify
        verify(authService).login(any(LoginRequest.class));
    }

    /** Verifies register delegates to authService and returns the newly created auth session payload. */
    @Test
    void register_ShouldReturnAuthSession_WhenRequestIsValid() {
        //arrange
        AuthSessionResponse response = new AuthSessionResponse(
            "new-access-token",
            "new-refresh-token",
            "Bearer",
            3600L,
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "new@example.com",
            false,
            null
        );
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        //act
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"email\": \"new@example.com\",
                  \"password\": \"StrongPass123!\",
                  \"displayName\": \"new_user\"
                }
                """)
        .when()
            .post("/api/auth/registrations")
        //assert
        .then()
            .status(HttpStatus.OK)
            .body("accessToken", equalTo("new-access-token"))
            .body("email", equalTo("new@example.com"));

        //verify
        verify(authService).register(any(RegisterRequest.class));
    }

    /** Verifies forgot-password triggers a password reset request and responds with 204. */
    @Test
    void forgotPassword_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange

        //act
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"email\": \"user@example.com\"
                }
                """)
        .when()
            .post("/api/auth/password-reset-requests")
        //assert
        .then()
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(authService).requestPasswordReset(any(ForgotPasswordRequest.class));
    }

    /** Verifies reset-password delegates to authService and responds with 204. */
    @Test
    void resetPassword_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange

        //act
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"accessToken\": \"reset-token\",
                  \"password\": \"StrongPass123!\"
                }
                """)
        .when()
            .put("/api/auth/password")
        //assert
        .then()
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(authService).resetPassword(any(ResetPasswordRequest.class));
    }

    /** Verifies logout passes through non-Bearer Authorization values unchanged. */
    @Test
    void logout_ShouldPassRawHeader_WhenAuthorizationHeaderIsNotBearer() {
        //arrange

        //act
        authenticated
            .header(HttpHeaders.AUTHORIZATION, "ApiKey abc-123")
        .when()
            .delete("/api/auth/session")
        //assert
        .then()
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(authService).logout(eq("ApiKey abc-123"));
    }

    /** Verifies logout passes null when Authorization header is absent. */
    @Test
    void logout_ShouldPassNull_WhenAuthorizationHeaderIsMissing() {
        //arrange

        //act
        authenticated
        .when()
            .delete("/api/auth/session")
        //assert
        .then()
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(authService).logout(eq(null));
    }

    /** Verifies logout passes null when Authorization header is blank. */
    @Test
    void logout_ShouldPassNull_WhenAuthorizationHeaderIsBlank() {
        //arrange

        //act
        authenticated
            .header(HttpHeaders.AUTHORIZATION, "   ")
        .when()
            .delete("/api/auth/session")
        //assert
        .then()
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(authService).logout(eq(null));
    }

    /** Verifies streak touch uses JWT subject, access token, and request timezone. */
    @Test
    void touchLoginStreak_ShouldReturnTouchResponse_WhenRequestContainsTimezone() {
        //arrange
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(loginStreakService.touchLoginStreak(any(UUID.class), anyString(), anyString()))
            .thenReturn(new LoginStreakTouchResponse(5, 10, LocalDate.of(2026, 4, 8), true));

        //act
        authenticated
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"timezone\": \"Asia/Singapore\"
                }
                """)
        .when()
            .put("/api/auth/login-streak")
        //assert
        .then()
            .status(HttpStatus.OK)
            .body("currentStreak", equalTo(5))
            .body("longestStreak", equalTo(10))
            .body("lastActivityDate", equalTo("2026-04-08"))
            .body("touchedToday", equalTo(true));

        //verify
        verify(loginStreakService).touchLoginStreak(eq(userId), eq("mocked-access-token"), eq("Asia/Singapore"));
    }

    /** Verifies streak touch accepts an empty request body and passes a null timezone. */
    @Test
    void touchLoginStreak_ShouldPassNullTimezone_WhenRequestBodyIsMissing() {
        //arrange
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(loginStreakService.touchLoginStreak(any(UUID.class), anyString(), any()))
            .thenReturn(new LoginStreakTouchResponse(1, 2, LocalDate.of(2026, 4, 8), true));

        //act
        authenticated
        .when()
            .put("/api/auth/login-streak")
        //assert
        .then()
            .status(HttpStatus.OK)
            .body("currentStreak", equalTo(1));

        //verify
        verify(loginStreakService).touchLoginStreak(eq(userId), eq("mocked-access-token"), eq(null));
    }

    /** Verifies username availability prefers displayName when it is provided and non-blank. */
    @Test
    void usernameAvailable_ShouldReturnAvailableWithNormalizedValue_WhenDisplayNameProvided() {
        //arrange
        when(userService.isDisplayNameFormatValid("Dot.User")).thenReturn(true);
        when(userService.normalizeDisplayName("Dot.User")).thenReturn("dot.user");
        when(userService.isDisplayNameTaken("dot.user")).thenReturn(false);

        //act
        given()
            .queryParam("displayName", "Dot.User")
            .queryParam("username", "ignored_username")
        .when()
            .get("/api/auth/display-name-availability")
        //assert
        .then()
            .status(HttpStatus.OK)
            .body("available", equalTo(true))
            .body("normalized", equalTo("dot.user"));

        //verify
        verify(userService).isDisplayNameFormatValid("Dot.User");
        verify(userService).normalizeDisplayName("Dot.User");
        verify(userService).isDisplayNameTaken("dot.user");
    }

    /** Verifies username availability falls back to username when displayName is blank. */
    @Test
    void usernameAvailable_ShouldUseUsernameFallback_WhenDisplayNameIsBlank() {
        //arrange
        when(userService.isDisplayNameFormatValid("fallback_user")).thenReturn(true);
        when(userService.normalizeDisplayName("fallback_user")).thenReturn("fallback_user");
        when(userService.isDisplayNameTaken("fallback_user")).thenReturn(true);

        //act
        given()
            .queryParam("displayName", "   ")
            .queryParam("username", "fallback_user")
        .when()
            .get("/api/auth/display-name-availability")
        //assert
        .then()
            .status(HttpStatus.OK)
            .body("available", equalTo(false))
            .body("normalized", equalTo("fallback_user"));

        //verify
        verify(userService).isDisplayNameFormatValid("fallback_user");
        verify(userService).normalizeDisplayName("fallback_user");
        verify(userService).isDisplayNameTaken("fallback_user");
    }

    /** Verifies username availability rejects requests missing both displayName and username. */
    @Test
    void usernameAvailable_ShouldReturnBadRequest_WhenBothDisplayNameAndUsernameAreMissing() {
        //arrange

        //act
        given()
        .when()
            .get("/api/auth/display-name-availability")
        //assert
        .then()
            .status(HttpStatus.BAD_REQUEST);

        //verify
    }

    /** Verifies username availability rejects malformed display names. */
    @Test
    void usernameAvailable_ShouldReturnBadRequest_WhenDisplayNameFormatIsInvalid() {
        //arrange
        when(userService.isDisplayNameFormatValid("bad name!!")).thenReturn(false);

        //act
        given()
            .queryParam("displayName", "bad name!!")
        .when()
            .get("/api/auth/display-name-availability")
        //assert
        .then()
            .status(HttpStatus.BAD_REQUEST);

        //verify
        verify(userService).isDisplayNameFormatValid("bad name!!");
    }

    @Test
    void legacyAuthAliases_ShouldStillWork() {
        AuthSessionResponse response = new AuthSessionResponse(
            "legacy-access-token",
            "legacy-refresh-token",
            "Bearer",
            3600L,
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "legacy@example.com",
            false,
            null
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(response);
        when(userService.isDisplayNameFormatValid("legacy_user")).thenReturn(true);
        when(userService.normalizeDisplayName("legacy_user")).thenReturn("legacy_user");
        when(userService.isDisplayNameTaken("legacy_user")).thenReturn(false);

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  \"email\": \"legacy@example.com\",
                  \"password\": \"StrongPass123!\"
                }
                """)
        .when()
            .post("/api/auth/login")
        .then()
            .status(HttpStatus.OK)
            .body("accessToken", equalTo("legacy-access-token"));

        authenticated
        .when()
            .post("/api/auth/logout")
        .then()
            .status(HttpStatus.NO_CONTENT);

        given()
            .queryParam("displayName", "legacy_user")
        .when()
            .get("/api/auth/username-available")
        .then()
            .status(HttpStatus.OK)
            .body("available", equalTo(true));
    }
}

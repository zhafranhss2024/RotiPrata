package com.rotiprata.api.auth.service;

import com.rotiprata.api.auth.dto.AuthSessionResponse;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.api.auth.request.ForgotPasswordRequest;
import com.rotiprata.api.auth.request.LoginRequest;
import com.rotiprata.api.auth.request.RegisterRequest;
import com.rotiprata.api.auth.request.ResetPasswordRequest;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseAuthClient;
import com.rotiprata.infrastructure.supabase.SupabaseSessionResponse;
import com.rotiprata.infrastructure.supabase.SupabaseSignupResponse;
import com.rotiprata.infrastructure.supabase.SupabaseUser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers auth service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService tests")
class AuthServiceImplTest {

    @Mock
    private SupabaseAuthClient supabaseAuthClient;

    @Mock
    private SupabaseAdminClient supabaseAdminClient;

    @Mock
    private UserService userService;

    private AuthService authService;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(supabaseAuthClient, supabaseAdminClient, userService, "http://localhost:5173");
    }

    /**
     * Verifies that login should return auth session when credentials are valid.
     */
    // Verifies login maps a successful provider response into API auth session fields.
    @Test
    void login_ShouldReturnAuthSession_WhenCredentialsAreValid() {
        // arrange
        UUID userId = UUID.randomUUID();
        SupabaseSessionResponse session = buildSession("user@example.com", userId.toString());
        when(supabaseAuthClient.login("user@example.com", "password")).thenReturn(session);

        // act
        AuthSessionResponse response = authService.login(new LoginRequest("user@example.com", "password"));

        // assert
        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals("bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals(userId, response.userId());
        assertEquals("user@example.com", response.email());
        assertFalse(response.requiresEmailConfirmation());

        // verify
        verify(supabaseAuthClient).login("user@example.com", "password");
    }

    /**
     * Verifies that login should throw forbidden when email not confirmed.
     */
    // Verifies login returns forbidden when provider reports unconfirmed email.
    @Test
    void login_ShouldThrowForbidden_WhenEmailNotConfirmed() {
        // arrange
        when(supabaseAuthClient.login(anyString(), anyString()))
            .thenThrow(buildException(HttpStatus.BAD_REQUEST, "please confirm your email"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(new LoginRequest("user@example.com", "password"))
        );

        // assert
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Email not confirmed. Check your inbox.", ex.getReason());

        // verify
        verify(supabaseAuthClient).login("user@example.com", "password");
    }

    /**
     * Verifies that login should throw unauthorized when provider returns unauthorized.
     */
    // Verifies login maps bad credentials responses to unauthorized.
    @Test
    void login_ShouldThrowUnauthorized_WhenProviderReturnsUnauthorized() {
        // arrange
        when(supabaseAuthClient.login(anyString(), anyString()))
            .thenThrow(buildException(HttpStatus.UNAUTHORIZED, "invalid login"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(new LoginRequest("user@example.com", "wrong"))
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid email or password", ex.getReason());

        // verify
        verify(supabaseAuthClient).login("user@example.com", "wrong");
    }

    /**
     * Verifies that login should throw bad gateway when provider returns unexpected status.
     */
    // Verifies login maps other provider failures to bad gateway.
    @Test
    void login_ShouldThrowBadGateway_WhenProviderReturnsUnexpectedStatus() {
        // arrange
        when(supabaseAuthClient.login(anyString(), anyString()))
            .thenThrow(buildException(HttpStatus.INTERNAL_SERVER_ERROR, null));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.login(new LoginRequest("user@example.com", "password"))
        );

        // assert
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).login("user@example.com", "password");
    }

    /**
     * Verifies that register should throw bad request when display name format is invalid.
     */
    // Verifies register fails fast when display name format is invalid.
    @Test
    void register_ShouldThrowBadRequest_WhenDisplayNameFormatIsInvalid() {
        // arrange
        when(userService.isDisplayNameFormatValid("bad name")).thenReturn(false);

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "bad name", false, null))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(userService).isDisplayNameFormatValid("bad name");
        verify(supabaseAdminClient, never()).emailExists(anyString());
    }

    /**
     * Verifies that register should throw conflict when email already registered before signup.
     */
    // Verifies register returns conflict when email already exists in admin lookup.
    @Test
    void register_ShouldThrowConflict_WhenEmailAlreadyRegisteredBeforeSignup() {
        // arrange
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(true);

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "newuser", false, null))
        );

        // assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email already registered", ex.getReason());

        // verify
        verify(supabaseAdminClient).emailExists("user@example.com");
        verify(supabaseAuthClient, never()).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that register should throw conflict when display name already taken.
     */
    // Verifies register returns conflict when normalized display name is already taken.
    @Test
    void register_ShouldThrowConflict_WhenDisplayNameAlreadyTaken() {
        // arrange
        when(userService.isDisplayNameFormatValid("NewUser")).thenReturn(true);
        when(userService.normalizeDisplayName("NewUser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(true);

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "NewUser", false, null))
        );

        // assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Display name already taken", ex.getReason());

        // verify
        verify(userService).isDisplayNameTaken("newuser");
        verify(supabaseAuthClient, never()).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that register should return pending confirmation when signup returns no session.
     */
    // Verifies register creates pending confirmation response when signup has no session.
    @Test
    void register_ShouldReturnPendingConfirmation_WhenSignupReturnsNoSession() {
        // arrange
        UUID userId = UUID.randomUUID();
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(null, buildUser("user@example.com", userId.toString())));

        // act
        AuthSessionResponse response = authService.register(
            new RegisterRequest("user@example.com", "Password123!", "newuser", true, null)
        );

        // assert
        assertNull(response.accessToken());
        assertEquals(userId, response.userId());
        assertEquals("user@example.com", response.email());
        assertTrue(response.requiresEmailConfirmation());

        // verify
        verify(userService).ensureProfileWithServiceRole(userId, "newuser", true, false);
    }

    /**
     * Verifies that register should fallback to request email when signup user is null.
     */
    // Verifies register falls back to request email when signup user payload is missing.
    @Test
    void register_ShouldFallbackToRequestEmail_WhenSignupUserIsNull() {
        // arrange
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(null, null));

        // act
        AuthSessionResponse response = authService.register(
            new RegisterRequest("user@example.com", "Password123!", "newuser", null, null)
        );

        // assert
        assertEquals("user@example.com", response.email());
        assertNull(response.userId());
        assertTrue(response.requiresEmailConfirmation());

        // verify
        verify(userService, never()).ensureProfileWithServiceRole(any(), anyString(), any(), anyBoolean());
    }

    /**
     * Verifies that register should use signup user email when signup user id is null.
     */
    // Verifies register uses signup user email when signup user exists without id.
    @Test
    void register_ShouldUseSignupUserEmail_WhenSignupUserIdIsNull() {
        // arrange
        SupabaseUser user = buildUser("signup@example.com", null);
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(null, user));

        // act
        AuthSessionResponse response = authService.register(
            new RegisterRequest("user@example.com", "Password123!", "newuser", false, null)
        );

        // assert
        assertEquals("signup@example.com", response.email());
        assertNull(response.userId());

        // verify
        verify(userService, never()).ensureProfileWithServiceRole(any(), anyString(), any(), anyBoolean());
    }

    /**
     * Verifies that register should return auth session when signup returns session.
     */
    // Verifies register returns active session response and ensures profile when session exists.
    @Test
    void register_ShouldReturnAuthSession_WhenSignupReturnsSession() {
        // arrange
        UUID userId = UUID.randomUUID();
        SupabaseSessionResponse session = buildSession("user@example.com", userId.toString());
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(session, buildUser("user@example.com", userId.toString())));

        // act
        AuthSessionResponse response = authService.register(
            new RegisterRequest("user@example.com", "Password123!", "newuser", false, null)
        );

        // assert
        assertEquals("access", response.accessToken());
        assertEquals(userId, response.userId());
        assertFalse(response.requiresEmailConfirmation());

        // verify
        verify(userService).ensureProfile(userId, "newuser", false, "access", false);
    }

    /**
     * Verifies that register should return session without user when session user is null.
     */
    // Verifies register supports session responses where session has no user payload.
    @Test
    void register_ShouldReturnSessionWithoutUser_WhenSessionUserIsNull() {
        // arrange
        SupabaseSessionResponse session = buildSession("ignored@example.com", null);
        session.setUser(null);
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(session, null));

        // act
        AuthSessionResponse response = authService.register(
            new RegisterRequest("user@example.com", "Password123!", "newuser", false, "http://custom/finish")
        );

        // assert
        assertNull(response.userId());
        assertNull(response.email());

        // verify
        verify(userService, never()).ensureProfile(any(), anyString(), any(), anyString(), anyBoolean());
    }

    /**
     * Verifies that register should send normalized metadata when signup is called.
     */
    // Verifies register includes all expected metadata and resolved redirect in signup request.
    @Test
    void register_ShouldSendNormalizedMetadata_WhenSignupIsCalled() {
        // arrange
        when(userService.isDisplayNameFormatValid("NewUser")).thenReturn(true);
        when(userService.normalizeDisplayName("NewUser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenReturn(buildSignupResponse(null, null));
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);

        // act
        authService.register(new RegisterRequest("user@example.com", "Password123!", "NewUser", true, "   "));

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient).signup(anyString(), anyString(), metadataCaptor.capture(), redirectCaptor.capture());
        assertEquals("newuser", metadataCaptor.getValue().get("display_name"));
        assertEquals("newuser", metadataCaptor.getValue().get("username"));
        assertEquals("newuser", metadataCaptor.getValue().get("preferred_username"));
        assertEquals(Boolean.TRUE, metadataCaptor.getValue().get("is_gen_alpha"));
        assertEquals("http://localhost:5173/auth/finish", redirectCaptor.getValue());
    }

    /**
     * Verifies that register should throw too many requests when provider returns429.
     */
    // Verifies register maps status 429 to too many requests.
    @Test
    void register_ShouldThrowTooManyRequests_WhenProviderReturns429() {
        // arrange
        primeRegisterPreconditions();
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(buildException(HttpStatus.TOO_MANY_REQUESTS, "rate limited"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "newuser", false, null))
        );

        // assert
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that register should throw too many requests when rate limit header is present.
     */
    // Verifies register maps provider header rate-limit code to too many requests.
    @Test
    void register_ShouldThrowTooManyRequests_WhenRateLimitHeaderIsPresent() {
        // arrange
        primeRegisterPreconditions();
        HttpHeaders headers = new HttpHeaders();
        headers.add("x_sb_error_code", "over_email_send_rate_limit");
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", headers, "body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "newuser", false, null))
        );

        // assert
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that register should throw conflict when provider body says already registered.
     */
    // Verifies register maps duplicate-email provider body to conflict.
    @Test
    void register_ShouldThrowConflict_WhenProviderBodySaysAlreadyRegistered() {
        // arrange
        primeRegisterPreconditions();
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(buildException(HttpStatus.BAD_REQUEST, "email address already exists"));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "newuser", false, null))
        );

        // assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email already registered", ex.getReason());

        // verify
        verify(supabaseAuthClient).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that register should throw bad request when provider error is unhandled.
     */
    // Verifies register maps unknown provider errors to bad request.
    @Test
    void register_ShouldThrowBadRequest_WhenProviderErrorIsUnhandled() {
        // arrange
        primeRegisterPreconditions();
        when(supabaseAuthClient.signup(anyString(), anyString(), anyMap(), anyString()))
            .thenThrow(buildException(HttpStatus.BAD_REQUEST, null));

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(new RegisterRequest("user@example.com", "Password123!", "newuser", false, null))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Unable to register user", ex.getReason());

        // verify
        verify(supabaseAuthClient).signup(anyString(), anyString(), anyMap(), anyString());
    }

    /**
     * Verifies that request password reset should call recover password when request is valid.
     */
    // Verifies password reset request delegates with resolved fallback redirect.
    @Test
    void requestPasswordReset_ShouldCallRecoverPassword_WhenRequestIsValid() {
        // arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com", null);

        // act
        authService.requestPasswordReset(request);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient).recoverPassword("user@example.com", "http://localhost:5173/auth/finish");
    }

    /**
     * Verifies that request password reset should throw too many requests when provider returns429.
     */
    // Verifies password reset request maps 429 to too many requests.
    @Test
    void requestPasswordReset_ShouldThrowTooManyRequests_WhenProviderReturns429() {
        // arrange
        doThrow(buildException(HttpStatus.TOO_MANY_REQUESTS, "rate limited"))
            .when(supabaseAuthClient).recoverPassword(anyString(), anyString());

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.requestPasswordReset(new ForgotPasswordRequest("user@example.com", null))
        );

        // assert
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).recoverPassword(anyString(), anyString());
    }

    /**
     * Verifies that request password reset should throw bad request when provider returns other error.
     */
    // Verifies password reset request maps other errors to bad request.
    @Test
    void requestPasswordReset_ShouldThrowBadRequest_WhenProviderReturnsOtherError() {
        // arrange
        doThrow(buildException(HttpStatus.BAD_GATEWAY, "upstream"))
            .when(supabaseAuthClient).recoverPassword(anyString(), anyString());

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.requestPasswordReset(new ForgotPasswordRequest("user@example.com", "http://custom/reset"))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).recoverPassword("user@example.com", "http://custom/reset");
    }

    /**
     * Verifies that request password reset should pass null redirect when frontend url and request redirect are blank.
     */
    // Verifies password reset passes null redirect when frontend url is blank and request redirect is blank.
    @Test
    void requestPasswordReset_ShouldPassNullRedirect_WhenFrontendUrlAndRequestRedirectAreBlank() {
        // arrange
        AuthService blankFrontend = new AuthServiceImpl(supabaseAuthClient, supabaseAdminClient, userService, " ");

        // act
        blankFrontend.requestPasswordReset(new ForgotPasswordRequest("user@example.com", "   "));

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient).recoverPassword("user@example.com", null);
    }

    /**
     * Verifies that reset password should call update password when request is valid.
     */
    // Verifies reset password delegates to provider update password API.
    @Test
    void resetPassword_ShouldCallUpdatePassword_WhenRequestIsValid() {
        // arrange
        ResetPasswordRequest request = new ResetPasswordRequest("access-token", "Password123!");

        // act
        authService.resetPassword(request);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient).updatePassword("access-token", "Password123!");
    }

    /**
     * Verifies that reset password should throw bad request when provider throws error.
     */
    // Verifies reset password maps provider errors to bad request.
    @Test
    void resetPassword_ShouldThrowBadRequest_WhenProviderThrowsError() {
        // arrange
        doThrow(buildException(HttpStatus.BAD_REQUEST, "bad token"))
            .when(supabaseAuthClient).updatePassword(anyString(), anyString());

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.resetPassword(new ResetPasswordRequest("access-token", "Password123!"))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).updatePassword("access-token", "Password123!");
    }

    /**
     * Verifies that logout should skip provider call when access token is null.
     */
    // Verifies logout exits early when token is null.
    @Test
    void logout_ShouldSkipProviderCall_WhenAccessTokenIsNull() {
        // arrange
        String accessToken = null;

        // act
        authService.logout(accessToken);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient, never()).logout(anyString());
    }

    /**
     * Verifies that logout should skip provider call when access token is blank.
     */
    // Verifies logout exits early when token is blank.
    @Test
    void logout_ShouldSkipProviderCall_WhenAccessTokenIsBlank() {
        // arrange
        String accessToken = "   ";

        // act
        authService.logout(accessToken);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient, never()).logout(anyString());
    }

    /**
     * Verifies that logout should call provider when access token is present.
     */
    // Verifies logout delegates when token is present.
    @Test
    void logout_ShouldCallProvider_WhenAccessTokenIsPresent() {
        // arrange
        String accessToken = "access-token";

        // act
        authService.logout(accessToken);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAuthClient).logout("access-token");
    }

    /**
     * Verifies that logout should throw bad request when provider throws error.
     */
    // Verifies logout maps provider failures to bad request.
    @Test
    void logout_ShouldThrowBadRequest_WhenProviderThrowsError() {
        // arrange
        doThrow(buildException(HttpStatus.BAD_REQUEST, "logout failed"))
            .when(supabaseAuthClient).logout("access-token");

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.logout("access-token")
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAuthClient).logout("access-token");
    }

    /**
     * Handles prime register preconditions.
     */
    private void primeRegisterPreconditions() {
        when(userService.isDisplayNameFormatValid("newuser")).thenReturn(true);
        when(userService.normalizeDisplayName("newuser")).thenReturn("newuser");
        when(supabaseAdminClient.emailExists("user@example.com")).thenReturn(false);
        when(userService.isDisplayNameTaken("newuser")).thenReturn(false);
    }

    /**
     * Builds the exception.
     */
    private RestClientResponseException buildException(HttpStatus status, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, bytes, StandardCharsets.UTF_8);
    }

    /**
     * Builds the session.
     */
    private SupabaseSessionResponse buildSession(String email, String userId) {
        SupabaseSessionResponse session = new SupabaseSessionResponse();
        session.setAccessToken("access");
        session.setRefreshToken("refresh");
        session.setTokenType("bearer");
        session.setExpiresIn(3600L);
        session.setUser(buildUser(email, userId));
        return session;
    }

    /**
     * Builds the user.
     */
    private SupabaseUser buildUser(String email, String userId) {
        SupabaseUser user = new SupabaseUser();
        user.setEmail(email);
        user.setId(userId);
        return user;
    }

    /**
     * Builds the signup response.
     */
    private SupabaseSignupResponse buildSignupResponse(SupabaseSessionResponse session, SupabaseUser user) {
        SupabaseSignupResponse response = new SupabaseSignupResponse();
        response.setSession(session);
        response.setUser(user);
        return response;
    }
}

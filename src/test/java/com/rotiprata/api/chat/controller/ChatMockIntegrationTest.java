package com.rotiprata.api.chat.controller;

import com.rotiprata.api.chat.service.ChatService;
import com.rotiprata.security.ChatRateLimiter;
import com.rotiprata.security.RateLimitExceededException;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
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

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("ChatController Mock Integration Tests")
class ChatControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatRateLimiter chatRateLimiter;

    private MockMvcRequestSpecification userAuth;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        userAuth = given().auth().with(jwt().jwt(j -> j
            .subject("user-123")
            .tokenValue("mocked-jwt-token")
        ));
    }

    // Verifies successful chat flow returns assistant reply using normalized question and valid JWT details.
    @Test
    void chat_ShouldReturnReply_WhenQuestionIsValid() {
        //arrange
        when(chatService.ask("mocked-jwt-token", "What is Roti Prata?"))
            .thenReturn("Roti Prata is a flaky flatbread.");

        //act
        userAuth
            .contentType(ContentType.TEXT)
            .body("What is Roti Prata?")
        .when()
            .post("/api/chat")
        //assert
        .then()
            .status(HttpStatus.OK)
            .contentType(ContentType.JSON)
            .body("reply", equalTo("Roti Prata is a flaky flatbread."));

        //verify
        verify(chatRateLimiter).consumeOrThrow("user-123");
        verify(chatService).ask("mocked-jwt-token", "What is Roti Prata?");
    }

    // Verifies validation error when question is blank after trim and avoids downstream dependencies.
    @Test
    void chat_ShouldReturnBadRequest_WhenQuestionIsBlank() {
        //arrange

        //act
        userAuth
            .contentType(ContentType.TEXT)
            .body("    ")
        .when()
            .post("/api/chat")
        //assert
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .contentType(ContentType.JSON)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Question is required"));

        //verify
        verify(chatRateLimiter, never()).consumeOrThrow(anyString());
        verify(chatService, never()).ask(anyString(), anyString());
    }

    // Verifies validation error for question length greater than 250 characters and prevents service execution.
    @Test
    void chat_ShouldReturnBadRequest_WhenQuestionExceedsMaxLength() {
        //arrange
        String longQuestion = "a".repeat(251);

        //act
        userAuth
            .contentType(ContentType.TEXT)
            .body(longQuestion)
        .when()
            .post("/api/chat")
        //assert
        .then()
            .status(HttpStatus.BAD_REQUEST)
            .contentType(ContentType.JSON)
            .body("code", equalTo("validation_error"))
            .body("message", equalTo("Question must be 250 characters or fewer"));

        //verify
        verify(chatRateLimiter, never()).consumeOrThrow(anyString());
        verify(chatService, never()).ask(anyString(), anyString());
    }

    // Verifies rate limit errors are translated to 429 with retry metadata and service invocation is skipped.
    @Test
    void chat_ShouldReturnTooManyRequests_WhenRateLimiterRejectsRequest() {
        //arrange
        org.mockito.Mockito.doThrow(new RateLimitExceededException("Too many chat prompts. Try again later.", 60L))
            .when(chatRateLimiter).consumeOrThrow("user-123");

        //act
        userAuth
            .contentType(ContentType.TEXT)
            .body("Hello")
        .when()
            .post("/api/chat")
        //assert
        .then()
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(ContentType.JSON)
            .header(HttpHeaders.RETRY_AFTER, equalTo("60"))
            .body("code", equalTo("rate_limited"))
            .body("message", equalTo("Too many chat prompts. Try again later."))
            .body("retryAfterSeconds", equalTo(60));

        //verify
        verify(chatRateLimiter).consumeOrThrow("user-123");
        verify(chatService, never()).ask(anyString(), anyString());
    }

    // Verifies runtime failures from chat service are mapped to 500 and still pass through the limiter first.
    @Test
    void chat_ShouldReturnInternalServerError_WhenChatServiceThrowsRuntimeException() {
        //arrange
        when(chatService.ask("mocked-jwt-token", "hello"))
            .thenThrow(new RuntimeException("Chat backend unavailable"));

        //act
        userAuth
            .contentType(ContentType.TEXT)
            .body("hello")
        .when()
            .post("/api/chat")
        //assert
        .then()
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(ContentType.JSON)
            .body("code", equalTo("error"))
            .body("message", equalTo("Chat backend unavailable"))
            .body("retryAfterSeconds", nullValue());

        //verify
        verify(chatRateLimiter).consumeOrThrow("user-123");
        verify(chatService).ask("mocked-jwt-token", "hello");
    }
}

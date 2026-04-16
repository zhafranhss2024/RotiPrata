package com.rotiprata.api.content.controller;

import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.dto.ContentCommentResponse;
import com.rotiprata.api.content.dto.ContentFlagRequest;
import com.rotiprata.api.content.dto.ContentMediaStartResponse;
import com.rotiprata.api.content.dto.ContentMediaStatusResponse;
import com.rotiprata.api.content.dto.ContentPlaybackEventRequest;
import com.rotiprata.api.content.dto.ContentQuizResponse;
import com.rotiprata.api.content.dto.ContentQuizSubmitResponse;
import com.rotiprata.api.content.service.ContentDraftService;
import com.rotiprata.api.content.service.ContentQuizService;
import com.rotiprata.api.content.service.ContentService;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.time.OffsetDateTime;
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

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Covers content controller scenarios and regression behavior for the current branch changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("ContentController Mock Integration Tests")
class ContentControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentDraftService contentDraftService;

    @MockBean
    private ContentService contentService;

    @MockBean
    private ContentQuizService contentQuizService;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    /**
     * Handles mocked user id.
     */
    private UUID mockedUserId() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    /**
     * Handles random id.
     */
    private UUID randomId() {
        return UUID.randomUUID();
    }

    /**
     * Verifies that start upload should return accepted when video file provided.
     */
    /** Verifies starting a media upload accepts a valid video file. */
    @Test
    void startUpload_ShouldReturnAccepted_WhenVideoFileProvided() {
        //arrange
        UUID contentId = randomId();
        when(contentDraftService.startUpload(any(), eq(com.rotiprata.api.content.domain.ContentType.VIDEO), any()))
            .thenReturn(new ContentMediaStartResponse(contentId, "processing", "/api/content/" + contentId + "/media"));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .multiPart("file", "video.mp4", "data".getBytes(), "video/mp4")
        .when()
            .post("/api/content/uploads")
        .then()
            //assert
            .status(HttpStatus.ACCEPTED)
            .contentType(ContentType.JSON)
            .body("status", equalTo("processing"));

        //verify
        verify(contentDraftService).startUpload(any(), eq(com.rotiprata.api.content.domain.ContentType.VIDEO), any());
    }

    /**
     * Verifies that start upload should return accepted when image file provided.
     */
    /** Verifies starting a media upload accepts a valid image file. */
    @Test
    void startUpload_ShouldReturnAccepted_WhenImageFileProvided() {
        //arrange
        when(contentDraftService.startUpload(any(), eq(com.rotiprata.api.content.domain.ContentType.IMAGE), any()))
            .thenReturn(new ContentMediaStartResponse(randomId(), "processing", "/api/content/poll"));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .multiPart("file", "image.png", "data".getBytes(), "image/png")
        .when()
            .post("/api/content/uploads")
        .then()
            //assert
            .status(HttpStatus.ACCEPTED);

        //verify
        verify(contentDraftService).startUpload(any(), eq(com.rotiprata.api.content.domain.ContentType.IMAGE), any());
    }

    /**
     * Verifies that start upload should return bad request when file is empty.
     */
    /** Verifies upload is rejected when no file content is supplied. */
    @Test
    void startUpload_ShouldReturnBadRequest_WhenFileIsEmpty() {
        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .multiPart("file", "empty.mp4", new byte[0], "video/mp4")
        .when()
            .post("/api/content/uploads")
        .then()
            //assert
            .status(HttpStatus.BAD_REQUEST)
            .body("message", equalTo("File is required"));

        //verify
        verify(contentDraftService, org.mockito.Mockito.never()).startUpload(any(), any(), any());
    }

    /**
     * Verifies that start upload should return bad request when content type missing.
     */
    /** Verifies upload is rejected when multipart content type is missing. */
    @Test
    void startUpload_ShouldReturnBadRequest_WhenContentTypeMissing() {
        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .multiPart("file", "upload.bin", "data".getBytes(), "application/octet-stream")
        .when()
            .post("/api/content/uploads")
        .then()
            //assert
            .status(HttpStatus.BAD_REQUEST)
            .body("message", equalTo("Only video or image uploads are supported"));

        //verify
        verify(contentDraftService, org.mockito.Mockito.never()).startUpload(any(), any(), any());
    }

    /**
     * Verifies that start upload should return bad request when content type unsupported.
     */
    /** Verifies upload is rejected for unsupported media content types. */
    @Test
    void startUpload_ShouldReturnBadRequest_WhenContentTypeUnsupported() {
        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .multiPart("file", "doc.txt", "data".getBytes(), "text/plain")
        .when()
            .post("/api/content/uploads")
        .then()
            //assert
            .status(HttpStatus.BAD_REQUEST)
            .body("message", equalTo("Only video or image uploads are supported"));

        //verify
        verify(contentDraftService, org.mockito.Mockito.never()).startUpload(any(), any(), any());
    }

    /**
     * Verifies that start link should return accepted when request is valid.
     */
    /** Verifies starting a link-based upload delegates to draft service. */
    @Test
    void startLink_ShouldReturnAccepted_WhenRequestIsValid() {
        //arrange
        when(contentDraftService.startLink(any(), anyString()))
            .thenReturn(new ContentMediaStartResponse(randomId(), "processing", "/api/content/poll"));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"sourceUrl":"https://example.com/video"}
                """)
        .when()
            .post("/api/content/link-imports")
        .then()
            //assert
            .status(HttpStatus.ACCEPTED)
            .body("status", equalTo("processing"));

        //verify
        verify(contentDraftService).startLink(any(), eq("https://example.com/video"));
    }

    /** Verifies draft patching returns updated content payload. */
    @Test
    void updateDraft_ShouldReturnOk_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();
        Content content = new Content();
        content.setId(contentId);
        content.setTitle("Updated Title");
        when(contentDraftService.updateDraft(any(), eq(contentId), any())).thenReturn(content);

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"title":"Updated Title"}
                """)
        .when()
            .patch("/api/content/{contentId}", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("title", equalTo("Updated Title"));

        //verify
        verify(contentDraftService).updateDraft(any(), eq(contentId), any());
    }

    /** Verifies submitting a draft returns the submitted content. */
    @Test
    void submit_ShouldReturnOk_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();
        Content content = new Content();
        content.setId(contentId);
        content.setTitle("Submitted");
        when(contentDraftService.submit(any(), eq(contentId), any())).thenReturn(content);

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {
                  "title":"Submitted",
                  "description":"Description",
                  "contentType":"VIDEO",
                  "tags":["tag1"]
                }
                """)
        .when()
            .post("/api/content/{contentId}/submission", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("title", equalTo("Submitted"));

        //verify
        verify(contentDraftService).submit(any(), eq(contentId), any());
    }

    /** Verifies media status fetch returns processing details. */
    @Test
    void mediaStatus_ShouldReturnOk_WhenDraftExists() {
        //arrange
        UUID contentId = randomId();
        when(contentDraftService.getMediaStatus(any(), eq(contentId)))
            .thenReturn(new ContentMediaStatusResponse("ready", "https://hls", "https://thumb", null));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .get("/api/content/{contentId}/media", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("status", equalTo("ready"))
            .body("errorMessage", nullValue());

        //verify
        verify(contentDraftService).getMediaStatus(any(), eq(contentId));
    }

    /** Verifies content details are returned for a valid content id. */
    @Test
    void getContent_ShouldReturnOk_WhenContentExists() {
        //arrange
        UUID contentId = randomId();
        when(contentService.getContentById(any(), eq(contentId), anyString()))
            .thenReturn(Map.of("id", contentId.toString(), "title", "Sample"));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .get("/api/content/{contentId}", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("title", equalTo("Sample"));

        //verify
        verify(contentService).getContentById(any(), eq(contentId), anyString());
    }

    /** Verifies similar content lookup returns service response for provided limit. */
    @Test
    void getSimilarContent_ShouldReturnOk_WhenLimitProvided() {
        //arrange
        UUID contentId = randomId();
        when(contentService.getSimilarContent(any(), eq(contentId), anyString(), eq(3)))
            .thenReturn(List.of(Map.of("id", "abc", "title", "Similar")));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .queryParam("limit", 3)
        .when()
            .get("/api/content/{contentId}/similar", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("[0].title", equalTo("Similar"));

        //verify
        verify(contentService).getSimilarContent(any(), eq(contentId), anyString(), eq(3));
    }

    /** Verifies view tracking endpoint accepts valid content id. */
    @Test
    void trackView_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .post("/api/content/{contentId}/views", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).trackView(any(), eq(contentId));
    }

    /** Verifies playback telemetry endpoint accepts validated request payload. */
    @Test
    void trackPlaybackEvent_ShouldReturnAccepted_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"startupMs":100,"stallCount":1,"watchMs":3000}
                """)
        .when()
            .post("/api/content/{contentId}/playback-events", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.ACCEPTED);

        //verify
        verify(contentService).recordPlaybackEvent(any(), eq(contentId), any(ContentPlaybackEventRequest.class));
    }

    /** Verifies like endpoint records a user like action. */
    @Test
    void like_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .post("/api/content/{contentId}/likes", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).likeContent(any(), eq(contentId), anyString());
    }

    /** Verifies unlike endpoint removes a previously liked content item. */
    @Test
    void unlike_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .delete("/api/content/{contentId}/likes", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).unlikeContent(any(), eq(contentId), anyString());
    }

    /** Verifies save endpoint persists content into user saved list. */
    @Test
    void save_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .post("/api/content/{contentId}/saves", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).saveContent(any(), eq(contentId), anyString());
    }

    /** Verifies unsave endpoint removes content from saved list. */
    @Test
    void unsave_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .delete("/api/content/{contentId}/saves", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).unsaveContent(any(), eq(contentId), anyString());
    }

    /** Verifies share endpoint tracks content share action. */
    @Test
    void share_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .post("/api/content/{contentId}/shares", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).shareContent(any(), eq(contentId), anyString());
    }

    /** Verifies quiz endpoint returns content quiz payload when quiz exists. */
    @Test
    void contentQuiz_ShouldReturnOk_WhenQuizExists() {
        //arrange
        UUID contentId = randomId();
        when(contentQuizService.getContentQuiz(any(), eq(contentId), anyString()))
            .thenReturn(new ContentQuizResponse(
                "quiz-1",
                null,
                contentId.toString(),
                "Quiz",
                "Desc",
                "multiple_choice",
                60,
                70,
                true,
                null,
                null,
                null,
                null,
                List.of()
            ));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .get("/api/content/{contentId}/quiz", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("title", equalTo("Quiz"));

        //verify
        verify(contentQuizService).getContentQuiz(any(), eq(contentId), anyString());
    }

    /** Verifies quiz endpoint returns 204 when no active quiz is configured. */
    @Test
    void contentQuiz_ShouldReturnNoContent_WhenQuizDoesNotExist() {
        //arrange
        UUID contentId = randomId();
        when(contentQuizService.getContentQuiz(any(), eq(contentId), anyString())).thenReturn(null);

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .get("/api/content/{contentId}/quiz", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentQuizService).getContentQuiz(any(), eq(contentId), anyString());
    }

    /** Verifies quiz submission endpoint returns grading summary. */
    @Test
    void submitContentQuiz_ShouldReturnOk_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();
        when(contentQuizService.submitContentQuiz(any(), eq(contentId), any(), anyString()))
            .thenReturn(new ContentQuizSubmitResponse(8, 10, 80.0, true));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"answers":{"q1":"A"},"timeTakenSeconds":30}
                """)
        .when()
            .post("/api/content/{contentId}/quiz-submissions", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("score", equalTo(8))
            .body("passed", equalTo(true));

        //verify
        verify(contentQuizService).submitContentQuiz(any(), eq(contentId), any(), anyString());
    }

    /** Verifies comments endpoint returns comment list with default pagination values. */
    @Test
    void comments_ShouldReturnOk_WhenUsingDefaultPagination() {
        //arrange
        UUID contentId = randomId();
        UUID commentId = randomId();
        when(contentService.listComments(any(), eq(contentId), eq(50), eq(0), anyString()))
            .thenReturn(List.of(new ContentCommentResponse(
                commentId,
                contentId,
                mockedUserId(),
                null,
                "Nice post",
                "Author",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00:00Z")
            )));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .get("/api/content/{contentId}/comments", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.OK)
            .body("[0].body", equalTo("Nice post"));

        //verify
        verify(contentService).listComments(any(), eq(contentId), eq(50), eq(0), anyString());
    }

    /** Verifies create comment endpoint returns created comment entity. */
    @Test
    void createComment_ShouldReturnCreated_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();
        UUID commentId = randomId();
        when(contentService.createComment(any(), eq(contentId), any(), anyString()))
            .thenReturn(new ContentCommentResponse(
                commentId,
                contentId,
                mockedUserId(),
                null,
                "Great",
                "Author",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00:00Z")
            ));

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"body":"Great"}
                """)
        .when()
            .post("/api/content/{contentId}/comments", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.CREATED)
            .body("body", equalTo("Great"));

        //verify
        verify(contentService).createComment(any(), eq(contentId), any(), anyString());
    }

    /** Verifies delete comment endpoint removes the target comment. */
    @Test
    void deleteComment_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();
        UUID commentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .delete("/api/content/{contentId}/comments/{commentId}", contentId.toString(), commentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).deleteComment(any(), eq(contentId), eq(commentId), anyString());
    }

    /** Verifies flag endpoint reports content using the provided reason payload. */
    @Test
    void flag_ShouldReturnNoContent_WhenRequestIsValid() {
        //arrange
        UUID contentId = randomId();

        //act
        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"reason":"spam","description":"Looks misleading"}
                """)
        .when()
            .post("/api/content/{contentId}/flags", contentId.toString())
        .then()
            //assert
            .status(HttpStatus.NO_CONTENT);

        //verify
        verify(contentService).flagContent(any(), eq(contentId), any(ContentFlagRequest.class), anyString());
    }

    @Test
    void legacyContentAliases_ShouldStillWork() {
        UUID contentId = randomId();
        when(contentDraftService.startLink(any(), anyString()))
            .thenReturn(new ContentMediaStartResponse(contentId, "processing", "/api/content/poll"));
        when(contentQuizService.submitContentQuiz(any(), eq(contentId), any(), anyString()))
            .thenReturn(new ContentQuizSubmitResponse(8, 10, 80.0, true));

        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"sourceUrl":"https://example.com/video"}
                """)
        .when()
            .post("/api/content/media/start-link")
        .then()
            .status(HttpStatus.ACCEPTED);

        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
        .when()
            .post("/api/content/{contentId}/like", contentId.toString())
        .then()
            .status(HttpStatus.NO_CONTENT);

        given()
            .auth().with(jwt().jwt(j -> j.subject(mockedUserId().toString()).tokenValue("mock-token")))
            .contentType(ContentType.JSON)
            .body("""
                {"answers":{"q1":"A"},"timeTakenSeconds":30}
                """)
        .when()
            .post("/api/content/{contentId}/quiz/submit", contentId.toString())
        .then()
            .status(HttpStatus.OK)
            .body("score", equalTo(8));
    }
}

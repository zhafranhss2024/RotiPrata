package com.rotiprata.api.lesson.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.api.admin.dto.AdminLessonCategoryMoveRequest;
import com.rotiprata.api.admin.dto.AdminLessonCategoryMoveResponse;
import com.rotiprata.api.admin.dto.AdminLessonDraftResponse;
import com.rotiprata.api.admin.dto.AdminPublishLessonResponse;
import com.rotiprata.api.admin.dto.AdminStepSaveRequest;
import com.rotiprata.api.admin.dto.AdminStepSaveResponse;
import com.rotiprata.api.lesson.dto.LessonFeedResponse;
import com.rotiprata.api.lesson.dto.LessonHeartsStatusResponse;
import com.rotiprata.api.lesson.dto.LessonHubResponse;
import com.rotiprata.api.lesson.dto.LessonHubSummaryResponse;
import com.rotiprata.api.lesson.dto.LessonMediaStartResponse;
import com.rotiprata.api.lesson.dto.LessonMediaStatusResponse;
import com.rotiprata.api.lesson.dto.LessonProgressResponse;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.lesson.dto.LessonQuizStateResponse;
import com.rotiprata.api.lesson.service.LessonQuizService;
import com.rotiprata.api.lesson.service.LessonService;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Covers lesson controller scenarios and regression behavior for the current branch changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LessonControllerMockIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LESSON_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SOURCE_CATEGORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TARGET_CATEGORY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String ACCESS_TOKEN = "lesson-mock-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LessonService lessonService;

    @MockBean
    private LessonQuizService lessonQuizService;

    private MockMvcRequestSpecification auth;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        auth = given()
            .auth().with(jwt().jwt(j -> j
                .subject(USER_ID.toString())
                .tokenValue(ACCESS_TOKEN)
            ));
    }

    /**
     * Verifies that lessons should return lesson list when authenticated.
     */
    /** Verifies lessons endpoint returns service results for authenticated users. */
    @Test
    void lessons_ShouldReturnLessonList_WhenAuthenticated() {
        //arrange
        when(lessonService.getLessons(anyString())).thenReturn(List.of(Map.of("id", LESSON_ID.toString())));

        //act
        var response = auth.when().get("/api/lessons");

        //assert
        response.then().status(HttpStatus.OK).body("[0].id", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).getLessons(eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson feed should return feed response when request is valid.
     */
    /** Verifies lesson feed endpoint maps query params and returns paged data. */
    @Test
    void lessonFeed_ShouldReturnFeedResponse_WhenRequestIsValid() {
        //arrange
        when(lessonService.getLessonFeed(anyString(), any())).thenReturn(new LessonFeedResponse(List.of(), false, 2, 5));

        //act
        var response = auth
            .queryParam("query", "greetings")
            .queryParam("difficulty", "easy")
            .queryParam("duration", "short")
            .queryParam("sort", "latest")
            .queryParam("page", "2")
            .queryParam("pageSize", "5")
            .when()
            .get("/api/lessons/feed");

        //assert
        response.then().status(HttpStatus.OK).body("page", equalTo(2)).body("pageSize", equalTo(5));

        //verify
        verify(lessonService).getLessonFeed(eq(ACCESS_TOKEN), any());
    }

    /**
     * Verifies that lesson hub should return hub response when authenticated.
     */
    /** Verifies lesson hub endpoint returns user-specific hub details. */
    @Test
    void lessonHub_ShouldReturnHubResponse_WhenAuthenticated() {
        //arrange
        when(lessonService.getLessonHub(any(), anyString()))
            .thenReturn(new LessonHubResponse(List.of(), new LessonHubSummaryResponse(10, 4, 7)));

        //act
        var response = auth.when().get("/api/lessons/hub");

        //assert
        response.then().status(HttpStatus.OK).body("summary.currentStreak", equalTo(7));

        //verify
        verify(lessonService).getLessonHub(eq(USER_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that search lessons should return search results when query provided.
     */
    /** Verifies lesson search endpoint passes query string to service. */
    @Test
    void searchLessons_ShouldReturnSearchResults_WhenQueryProvided() {
        //arrange
        when(lessonService.searchLessons(anyString(), anyString())).thenReturn(List.of(Map.of("title", "Bonjour")));

        //act
        var response = auth.queryParam("q", "bon").when().get("/api/lessons/search-results");

        //assert
        response.then().status(HttpStatus.OK).body("[0].title", equalTo("Bonjour"));

        //verify
        verify(lessonService).searchLessons(eq("bon"), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson by id should return lesson when lesson exists.
     */
    /** Verifies lesson by id endpoint returns lesson payload. */
    @Test
    void lessonById_ShouldReturnLesson_WhenLessonExists() {
        //arrange
        when(lessonService.getLessonById(any(), anyString())).thenReturn(Map.of("id", LESSON_ID.toString()));

        //act
        var response = auth.when().get("/api/lessons/{lessonId}", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("id", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).getLessonById(eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson sections should return section list when lesson exists.
     */
    /** Verifies lesson sections endpoint returns section list. */
    @Test
    void lessonSections_ShouldReturnSectionList_WhenLessonExists() {
        //arrange
        when(lessonService.getLessonSections(any(), anyString())).thenReturn(List.of(Map.of("id", "intro")));

        //act
        var response = auth.when().get("/api/lessons/{lessonId}/sections", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("[0].id", equalTo("intro"));

        //verify
        verify(lessonService).getLessonSections(eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson progress should return progress when authenticated.
     */
    /** Verifies lesson progress endpoint returns progress snapshot. */
    @Test
    void lessonProgress_ShouldReturnProgress_WhenAuthenticated() {
        //arrange
        when(lessonService.getLessonProgress(any(), any(), anyString()))
            .thenReturn(new LessonProgressResponse("in_progress", 40, "intro", 1, 3, "def", true, 4, 2, "intro", 2, "available", 4, OffsetDateTime.now(), "section"));

        //act
        var response = auth.when().get("/api/lessons/{lessonId}/progress", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("progressPercentage", equalTo(40)).body("heartsRemaining", equalTo(4));

        //verify
        verify(lessonService).getLessonProgress(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson quiz state should return quiz state when authenticated.
     */
    /** Verifies quiz state endpoint returns active attempt state. */
    @Test
    void lessonQuizState_ShouldReturnQuizState_WhenAuthenticated() {
        //arrange
        when(lessonQuizService.getQuizState(any(), any(), anyString()))
            .thenReturn(new LessonQuizStateResponse("attempt-1", "in_progress", 0, 5, 0, 0, 50, null, new LessonHeartsStatusResponse(5, OffsetDateTime.now()), true, true, List.of()));

        //act
        var response = auth.when().get("/api/lessons/{lessonId}/quiz/state", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("attemptId", equalTo("attempt-1"));

        //verify
        verify(lessonQuizService).getQuizState(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that answer lesson quiz should return answer response when payload is valid.
     */
    /** Verifies answer quiz endpoint forwards answer payload and returns grading result. */
    @Test
    void answerLessonQuiz_ShouldReturnAnswerResponse_WhenPayloadIsValid() throws Exception {
        //arrange
        when(lessonQuizService.answerQuestion(any(), any(), any(), anyString()))
            .thenReturn(new LessonQuizAnswerResponse("attempt-1", "in_progress", true, "Great", 1, 5, 1, 10, 50, false, false, false, null, null, List.of()));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("attemptId", "attempt-1", "questionId", "q1", "response", Map.of("answer", "A"))))
            .when()
            .post("/api/lessons/{lessonId}/quiz/answers", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("correct", equalTo(true));

        //verify
        verify(lessonQuizService).answerQuestion(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that restart lesson quiz should return quiz state when mode provided.
     */
    /** Verifies restart quiz endpoint supports custom restart mode. */
    @Test
    void restartLessonQuiz_ShouldReturnQuizState_WhenModeProvided() {
        //arrange
        when(lessonQuizService.restartQuiz(any(), any(), anyString(), anyString()))
            .thenReturn(new LessonQuizStateResponse("attempt-2", "in_progress", 0, 3, 0, 0, 30, null, null, true, true, List.of()));

        //act
        var response = auth.queryParam("mode", "retry_wrong").when().post("/api/lessons/{lessonId}/quiz-attempts", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("attemptId", equalTo("attempt-2"));

        //verify
        verify(lessonQuizService).restartQuiz(eq(USER_ID), eq(LESSON_ID), eq("retry_wrong"), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that complete lesson section should return wrapped progress when section completed.
     */
    /** Verifies complete section endpoint wraps progress inside SectionCompleteResponse. */
    @Test
    void completeLessonSection_ShouldReturnWrappedProgress_WhenSectionCompleted() {
        //arrange
        when(lessonService.completeLessonSection(any(), any(), anyString(), anyString()))
            .thenReturn(new LessonProgressResponse("in_progress", 60, "usage", 2, 3, "quiz", true, 4, 3, "usage", 1, "available", 5, OffsetDateTime.now(), "quiz"));

        //act
        var response = auth.when().put("/api/lessons/{lessonId}/sections/{sectionId}/completion", LESSON_ID.toString(), "usage");

        //assert
        response.then().status(HttpStatus.OK).body("progress.progressPercentage", equalTo(60));

        //verify
        verify(lessonService).completeLessonSection(eq(USER_ID), eq(LESSON_ID), eq("usage"), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that complete lesson section post completion should return wrapped progress when section completed.
     */
    /** Verifies legacy POST completion alias still wraps progress inside SectionCompleteResponse. */
    @Test
    void completeLessonSectionPostCompletion_ShouldReturnWrappedProgress_WhenSectionCompleted() {
        //arrange
        when(lessonService.completeLessonSection(any(), any(), anyString(), anyString()))
            .thenReturn(new LessonProgressResponse("in_progress", 60, "usage", 2, 3, "quiz", true, 4, 3, "usage", 1, "available", 5, OffsetDateTime.now(), "quiz"));

        //act
        var response = auth.when().post("/api/lessons/{lessonId}/sections/{sectionId}/completion", LESSON_ID.toString(), "usage");

        //assert
        response.then().status(HttpStatus.OK).body("progress.progressPercentage", equalTo(60));

        //verify
        verify(lessonService).completeLessonSection(eq(USER_ID), eq(LESSON_ID), eq("usage"), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that enroll lesson should return ok when enrollment succeeds.
     */
    /** Verifies enroll endpoint delegates enrollment to service. */
    @Test
    void enrollLesson_ShouldReturnOk_WhenEnrollmentSucceeds() {
        //arrange

        //act
        var response = auth.when().put("/api/lessons/{lessonId}/enrollment", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK);

        //verify
        verify(lessonService).enrollLesson(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that save lesson should return ok when save succeeds.
     */
    /** Verifies save endpoint delegates bookmark/save action to service. */
    @Test
    void saveLesson_ShouldReturnOk_WhenSaveSucceeds() {
        //arrange

        //act
        var response = auth.when().put("/api/lessons/{lessonId}/saved", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK);

        //verify
        verify(lessonService).saveLesson(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that update progress should return ok when payload is valid.
     */
    /** Verifies progress update endpoint forwards bounded progress payload. */
    @Test
    void updateProgress_ShouldReturnOk_WhenPayloadIsValid() throws Exception {
        //arrange

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("progress", 80)))
            .when()
            .put("/api/lessons/{lessonId}/progress", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK);

        //verify
        verify(lessonService).updateLessonProgress(eq(USER_ID), eq(LESSON_ID), eq(80), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that admin lessons should return lesson list when authenticated.
     */
    /** Verifies admin lessons endpoint returns admin lesson list. */
    @Test
    void adminLessons_ShouldReturnLessonList_WhenAuthenticated() {
        //arrange
        when(lessonService.getAdminLessons(any(), anyString())).thenReturn(List.of(Map.of("id", LESSON_ID.toString())));

        //act
        var response = auth.when().get("/api/admin/lessons");

        //assert
        response.then().status(HttpStatus.OK).body("[0].id", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).getAdminLessons(eq(USER_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that admin lesson by id should return lesson when lesson exists.
     */
    /** Verifies admin lesson by id endpoint returns lesson details. */
    @Test
    void adminLessonById_ShouldReturnLesson_WhenLessonExists() {
        //arrange
        when(lessonService.getAdminLessonById(any(), any(), anyString())).thenReturn(Map.of("id", LESSON_ID.toString()));

        //act
        var response = auth.when().get("/api/admin/lessons/{lessonId}", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("id", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).getAdminLessonById(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that create lesson draft should return draft response when payload provided.
     */
    /** Verifies draft creation endpoint handles nullable payload and returns draft info. */
    @Test
    void createLessonDraft_ShouldReturnDraftResponse_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.createLessonDraft(any(), any(), anyString()))
            .thenReturn(new AdminLessonDraftResponse(LESSON_ID, Map.of("metadata", true), Map.of("id", LESSON_ID.toString())));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("title", "Intro Lesson")))
            .when()
            .post("/api/admin/lesson-drafts");

        //assert
        response.then().status(HttpStatus.OK).body("lessonId", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).createLessonDraft(eq(USER_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that save lesson draft step should return step response when payload provided.
     */
    /** Verifies draft step save endpoint persists requested draft step. */
    @Test
    void saveLessonDraftStep_ShouldReturnStepResponse_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.saveLessonStep(any(), any(), anyString(), any(), anyString()))
            .thenReturn(new AdminStepSaveResponse("metadata", true, List.of(), Map.of(), Map.of("metadata", true)));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("lesson", Map.of("title", "Updated"), "questions", List.of())))
            .when()
            .put("/api/admin/lesson-drafts/{lessonId}/steps/{stepKey}", LESSON_ID.toString(), "metadata");

        //assert
        response.then().status(HttpStatus.OK).body("step", equalTo("metadata"));

        //verify
        verify(lessonService).saveLessonStep(eq(USER_ID), eq(LESSON_ID), eq("metadata"), any(AdminStepSaveRequest.class), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that publish lesson should return publish response when request provided.
     */
    /** Verifies publish endpoint validates and publishes a lesson draft. */
    @Test
    void publishLesson_ShouldReturnPublishResponse_WhenRequestProvided() throws Exception {
        //arrange
        when(lessonService.publishLessonWithValidation(any(), any(), any(), anyString()))
            .thenReturn(new AdminPublishLessonResponse(true, null, List.of(), Map.of("id", LESSON_ID.toString())));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("lesson", Map.of("title", "Ready"), "questions", List.of())))
            .when()
            .post("/api/admin/lessons/{lessonId}/publication", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("success", equalTo(true));

        //verify
        verify(lessonService).publishLessonWithValidation(eq(USER_ID), eq(LESSON_ID), any(AdminStepSaveRequest.class), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that create lesson should return created lesson when payload provided.
     */
    /** Verifies admin create lesson endpoint returns created lesson payload. */
    @Test
    void createLesson_ShouldReturnCreatedLesson_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.createLesson(any(), any(), anyString())).thenReturn(Map.of("id", LESSON_ID.toString()));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("title", "Created")))
            .when()
            .post("/api/admin/lessons");

        //assert
        response.then().status(HttpStatus.OK).body("id", equalTo(LESSON_ID.toString()));

        //verify
        verify(lessonService).createLesson(eq(USER_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that update lesson should return updated lesson when payload provided.
     */
    /** Verifies admin update lesson endpoint returns updated lesson payload. */
    @Test
    void updateLesson_ShouldReturnUpdatedLesson_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.updateLesson(any(), any(), any(), anyString())).thenReturn(Map.of("title", "Updated"));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("title", "Updated")))
            .when()
            .put("/api/admin/lessons/{lessonId}", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("title", equalTo("Updated"));

        //verify
        verify(lessonService).updateLesson(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that delete lesson should return ok when deletion succeeds.
     */
    /** Verifies admin delete lesson endpoint delegates deletion to service. */
    @Test
    void deleteLesson_ShouldReturnOk_WhenDeletionSucceeds() {
        //arrange

        //act
        var response = auth.when().delete("/api/admin/lessons/{lessonId}", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK);

        //verify
        verify(lessonService).deleteLesson(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that move lesson to category should return move response when request is valid.
     */
    /** Verifies move category endpoint forwards source and target category ids. */
    @Test
    void moveLessonToCategory_ShouldReturnMoveResponse_WhenRequestIsValid() throws Exception {
        //arrange
        when(lessonService.moveLessonToCategory(any(), any(), any(), anyString()))
            .thenReturn(new AdminLessonCategoryMoveResponse(SOURCE_CATEGORY_ID, TARGET_CATEGORY_ID, List.of(), List.of(), Map.of("id", LESSON_ID.toString())));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("sourceCategoryId", SOURCE_CATEGORY_ID, "targetCategoryId", TARGET_CATEGORY_ID)))
            .when()
            .put("/api/admin/lessons/{lessonId}/category", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("sourceCategoryId", equalTo(SOURCE_CATEGORY_ID.toString()));

        //verify
        verify(lessonService).moveLessonToCategory(eq(USER_ID), eq(LESSON_ID), any(AdminLessonCategoryMoveRequest.class), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that get lesson quiz should return quiz questions when lesson has quiz.
     */
    /** Verifies admin quiz endpoint returns active quiz questions. */
    @Test
    void getLessonQuiz_ShouldReturnQuizQuestions_WhenLessonHasQuiz() {
        //arrange
        when(lessonService.getActiveLessonQuizQuestions(any(), any(), anyString())).thenReturn(List.of(Map.of("question", "Q1")));

        //act
        var response = auth.when().get("/api/admin/lessons/{lessonId}/quiz", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("[0].question", equalTo("Q1"));

        //verify
        verify(lessonService).getActiveLessonQuizQuestions(eq(USER_ID), eq(LESSON_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that get admin quiz question types should return types when authenticated.
     */
    /** Verifies admin question types endpoint returns available quiz question types. */
    @Test
    void getAdminQuizQuestionTypes_ShouldReturnTypes_WhenAuthenticated() {
        //arrange
        when(lessonService.getAdminQuizQuestionTypes(any(), anyString())).thenReturn(List.of(Map.of("key", "multiple_choice")));

        //act
        var response = auth.when().get("/api/admin/quiz/question-types");

        //assert
        response.then().status(HttpStatus.OK).body("[0].key", equalTo("multiple_choice"));

        //verify
        verify(lessonService).getAdminQuizQuestionTypes(eq(USER_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that create lesson quiz should return quiz payload when payload provided.
     */
    /** Verifies admin create quiz endpoint returns created quiz payload. */
    @Test
    void createLessonQuiz_ShouldReturnQuizPayload_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.createLessonQuiz(any(), any(), any(), anyString())).thenReturn(Map.of("quizId", "quiz-1"));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("questions", List.of(Map.of("id", "q1")))))
            .when()
            .post("/api/admin/lessons/{lessonId}/quiz", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("quizId", equalTo("quiz-1"));

        //verify
        verify(lessonService).createLessonQuiz(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that replace lesson quiz should return quiz list when payload provided.
     */
    /** Verifies admin replace quiz endpoint returns replacement question list. */
    @Test
    void replaceLessonQuiz_ShouldReturnQuizList_WhenPayloadProvided() throws Exception {
        //arrange
        when(lessonService.replaceLessonQuiz(any(), any(), any(), anyString())).thenReturn(List.of(Map.of("questionId", "q2")));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("questions", List.of(Map.of("id", "q2")))))
            .when()
            .put("/api/admin/lessons/{lessonId}/quiz", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("[0].questionId", equalTo("q2"));

        //verify
        verify(lessonService).replaceLessonQuiz(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that start lesson media upload should return media start response when file provided.
     */
    /** Verifies media upload endpoint accepts multipart file and returns polling details. */
    @Test
    void startLessonMediaUpload_ShouldReturnMediaStartResponse_WhenFileProvided() throws Exception {
        //arrange
        when(lessonService.startLessonMediaUpload(any(), any(), any(), anyString()))
            .thenReturn(new LessonMediaStartResponse(ASSET_ID, "processing", "/api/admin/lessons/asset"));

        MockMultipartFile file = new MockMultipartFile("file", "audio.mp3", MediaType.APPLICATION_OCTET_STREAM_VALUE, "data".getBytes());

        //act
        var response = given()
            .auth().with(jwt().jwt(j -> j.subject(USER_ID.toString()).tokenValue(ACCESS_TOKEN)))
            .multiPart("file", file.getOriginalFilename(), file.getBytes(), file.getContentType())
            .when()
            .post("/api/admin/lessons/{lessonId}/media-uploads", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("assetId", equalTo(ASSET_ID.toString()));

        //verify
        verify(lessonService).startLessonMediaUpload(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that start lesson media link should return media start response when payload is valid.
     */
    /** Verifies media link endpoint validates link payload and returns upload metadata. */
    @Test
    void startLessonMediaLink_ShouldReturnMediaStartResponse_WhenPayloadIsValid() throws Exception {
        //arrange
        when(lessonService.startLessonMediaLink(any(), any(), any(), anyString()))
            .thenReturn(new LessonMediaStartResponse(ASSET_ID, "queued", "/api/admin/lessons/asset"));

        //act
        var response = auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("sourceUrl", "https://cdn.example.com/media.mp4", "mediaKind", "video")))
            .when()
            .post("/api/admin/lessons/{lessonId}/media-link-imports", LESSON_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("status", equalTo("queued"));

        //verify
        verify(lessonService).startLessonMediaLink(eq(USER_ID), eq(LESSON_ID), any(), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that lesson media status should return status response when asset exists.
     */
    /** Verifies media status endpoint returns latest processing status for an asset. */
    @Test
    void lessonMediaStatus_ShouldReturnStatusResponse_WhenAssetExists() {
        //arrange
        when(lessonService.getLessonMediaStatus(any(), any(), any(), anyString()))
            .thenReturn(new LessonMediaStatusResponse(ASSET_ID, "ready", "video", "https://play", "https://thumb", null));

        //act
        var response = auth.when().get("/api/admin/lessons/{lessonId}/media/{assetId}", LESSON_ID.toString(), ASSET_ID.toString());

        //assert
        response.then().status(HttpStatus.OK).body("status", equalTo("ready"));

        //verify
        verify(lessonService).getLessonMediaStatus(eq(USER_ID), eq(LESSON_ID), eq(ASSET_ID), eq(ACCESS_TOKEN));
    }

    /**
     * Verifies that legacy lesson aliases should still work.
     */
    @Test
    void legacyLessonAliases_ShouldStillWork() throws Exception {
        when(lessonQuizService.restartQuiz(any(), any(), anyString(), anyString()))
            .thenReturn(new LessonQuizStateResponse("attempt-2", "in_progress", 0, 3, 0, 0, 30, null, null, true, true, List.of()));
        when(lessonService.createLessonDraft(any(), any(), anyString()))
            .thenReturn(new AdminLessonDraftResponse(LESSON_ID, Map.of("metadata", true), Map.of("id", LESSON_ID.toString())));

        auth
            .queryParam("mode", "retry_wrong")
        .when()
            .post("/api/lessons/{lessonId}/quiz/restart", LESSON_ID.toString())
        .then()
            .status(HttpStatus.OK)
            .body("attemptId", equalTo("attempt-2"));

        auth
            .contentType(ContentType.JSON)
            .body(objectMapper.writeValueAsString(Map.of("title", "Intro Lesson")))
        .when()
            .post("/api/admin/lessons/draft")
        .then()
            .status(HttpStatus.OK)
            .body("lessonId", equalTo(LESSON_ID.toString()));

        auth
        .when()
            .post("/api/lessons/{lessonId}/enroll", LESSON_ID.toString())
        .then()
            .status(HttpStatus.OK);
    }
}

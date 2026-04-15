package com.rotiprata.api.lesson.service;

import com.rotiprata.api.lesson.dto.LessonHeartsStatusResponse;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerRequest;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.lesson.dto.LessonQuizStateResponse;
import com.rotiprata.api.lesson.utils.LessonFlowConstants;
import com.rotiprata.api.lesson.utils.quiz.LessonQuizGraderRegistry;
import com.rotiprata.api.lesson.utils.quiz.MultipleChoiceQuestionGrader;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LessonQuizServiceImplTest {

    private static final String ACCESS_TOKEN = "token";

    @Mock
    private SupabaseRestClient supabaseRestClient;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    private LessonQuizServiceImpl lessonQuizService;
    private UUID userId;
    private UUID lessonId;
    private String quizId;
    private String firstQuestionId;
    private String secondQuestionId;
    private Map<String, Object> lesson;
    private Map<String, Object> quiz;
    private Map<String, Object> firstQuestion;
    private Map<String, Object> secondQuestion;

    @BeforeEach
    void setUp() {
        lessonQuizService = new LessonQuizServiceImpl(
            supabaseRestClient,
            supabaseAdminRestClient,
            new LessonQuizGraderRegistry(List.of(new MultipleChoiceQuestionGrader()))
        );
        userId = UUID.randomUUID();
        lessonId = UUID.randomUUID();
        quizId = UUID.randomUUID().toString();
        firstQuestionId = UUID.randomUUID().toString();
        secondQuestionId = UUID.randomUUID().toString();
        lesson = learnerLesson();
        quiz = Map.of("id", quizId, "lesson_id", lessonId.toString(), "is_active", true);
        firstQuestion = multipleChoiceQuestion(firstQuestionId, 0);
        secondQuestion = multipleChoiceQuestion(secondQuestionId, 1);
    }

    @Test
    void answerQuestion_ShouldReturnPersistedHearts_WhenWrongAnswerConsumesHeart() {
        // arrange
        Map<String, Object> attempt = activeAttempt("attempt-1", 0, 0, 0, 20, "in_progress");
        Map<String, Object> patchedAttempt = activeAttempt("attempt-1", 1, 0, 0, 20, "in_progress");
        OffsetDateTime scheduledRefillAt = OffsetDateTime.now().plusHours(6);
        OffsetDateTime consumedRefillAt = OffsetDateTime.now().plusHours(24);
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, scheduledRefillAt)), List.of(heartsRow(4, consumedRefillAt)));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(attempt));
        when(supabaseRestClient.patchList(eq("user_lesson_quiz_attempts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(patchedAttempt));
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(4, consumedRefillAt)));

        // act
        LessonQuizAnswerResponse response = lessonQuizService.answerQuestion(
            userId,
            lessonId,
            new LessonQuizAnswerRequest("attempt-1", firstQuestionId, Map.of("choiceId", "B")),
            ACCESS_TOKEN
        );

        // assert
        assertFalse(response.correct());
        assertEquals(LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS, response.status());
        assertEquals(4, response.hearts().heartsRemaining());
        assertNotNull(response.hearts().heartsRefillAt());
        assertNotNull(response.nextQuestion());

        // verify
        verify(supabaseRestClient).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> heartsPatchCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(supabaseRestClient).patchList(eq("user_quiz_hearts"), anyString(), heartsPatchCaptor.capture(), eq(ACCESS_TOKEN), any());
        OffsetDateTime updatedAt = (OffsetDateTime) heartsPatchCaptor.getValue().get("updated_at");
        OffsetDateTime refillAt = (OffsetDateTime) heartsPatchCaptor.getValue().get("refill_at");
        assertNotNull(updatedAt);
        assertEquals(updatedAt.plusHours(24), refillAt);
    }

    @Test
    void answerQuestion_ShouldKeepDeductedHearts_WhenFullHeartsRowHasStaleRefillTime() {
        // arrange
        Map<String, Object> attempt = activeAttempt("attempt-1", 0, 0, 0, 20, "in_progress");
        Map<String, Object> patchedAttempt = activeAttempt("attempt-1", 1, 0, 0, 20, "in_progress");
        OffsetDateTime staleRefillAt = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime normalizedRefillAt = OffsetDateTime.now().plusHours(24);
        OffsetDateTime consumedRefillAt = OffsetDateTime.now().plusHours(24);
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(
                List.of(heartsRow(5, staleRefillAt)),
                List.of(heartsRow(5, normalizedRefillAt)),
                List.of(heartsRow(4, consumedRefillAt)),
                List.of(heartsRow(4, consumedRefillAt))
            );
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(attempt));
        when(supabaseRestClient.patchList(eq("user_lesson_quiz_attempts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(patchedAttempt));
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, normalizedRefillAt)), List.of(heartsRow(4, consumedRefillAt)));

        // act
        LessonQuizAnswerResponse response = lessonQuizService.answerQuestion(
            userId,
            lessonId,
            new LessonQuizAnswerRequest("attempt-1", firstQuestionId, Map.of("choiceId", "B")),
            ACCESS_TOKEN
        );
        LessonHeartsStatusResponse refreshedHearts = lessonQuizService.getHeartsStatus(userId, ACCESS_TOKEN);

        // assert
        assertEquals(4, response.hearts().heartsRemaining());
        assertNotNull(response.hearts().heartsRefillAt());
        assertEquals(4, refreshedHearts.heartsRemaining());
        assertNotNull(refreshedHearts.heartsRefillAt());

        // verify
        verify(supabaseRestClient, times(2)).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> heartsPatchCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(supabaseRestClient, times(2)).patchList(eq("user_quiz_hearts"), anyString(), heartsPatchCaptor.capture(), eq(ACCESS_TOKEN), any());
        for (Map<String, Object> patch : heartsPatchCaptor.getAllValues()) {
            assertNotNull(patch.get("refill_at"));
        }
    }

    @Test
    void answerQuestion_ShouldKeepHeartsUnchanged_WhenAnswerIsCorrect() {
        // arrange
        Map<String, Object> attempt = activeAttempt("attempt-1", 0, 0, 0, 20, "in_progress");
        Map<String, Object> patchedAttempt = activeAttempt("attempt-1", 1, 1, 10, 20, "in_progress");
        OffsetDateTime scheduledRefillAt = OffsetDateTime.now().plusHours(12);
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, scheduledRefillAt)));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(attempt));
        when(supabaseRestClient.patchList(eq("user_lesson_quiz_attempts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(patchedAttempt));

        // act
        LessonQuizAnswerResponse response = lessonQuizService.answerQuestion(
            userId,
            lessonId,
            new LessonQuizAnswerRequest("attempt-1", firstQuestionId, Map.of("choiceId", "A")),
            ACCESS_TOKEN
        );

        // assert
        assertTrue(response.correct());
        assertEquals(LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS, response.status());
        assertEquals(5, response.hearts().heartsRemaining());
        assertNotNull(response.hearts().heartsRefillAt());

        // verify
        verify(supabaseRestClient, never()).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
    }

    @Test
    void answerQuestion_ShouldBlockQuiz_WhenWrongAnswerConsumesLastHeart() {
        // arrange
        Map<String, Object> attempt = activeAttempt("attempt-1", 0, 0, 0, 20, "in_progress");
        Map<String, Object> patchedAttempt = activeAttempt("attempt-1", 1, 0, 0, 20, "paused_no_hearts");
        OffsetDateTime refillAt = OffsetDateTime.parse("2026-04-13T10:00:00Z");
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(1, refillAt)), List.of(heartsRow(0, refillAt)));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(attempt));
        when(supabaseRestClient.patchList(eq("user_lesson_quiz_attempts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(patchedAttempt));
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(0, refillAt)));

        // act
        LessonQuizAnswerResponse response = lessonQuizService.answerQuestion(
            userId,
            lessonId,
            new LessonQuizAnswerRequest("attempt-1", firstQuestionId, Map.of("choiceId", "B")),
            ACCESS_TOKEN
        );

        // assert
        assertEquals(LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS, response.status());
        assertTrue(response.blockedByHearts());
        assertEquals(0, response.hearts().heartsRemaining());
        assertNull(response.nextQuestion());
    }

    @Test
    void getQuizState_ShouldReturnBlockedState_WhenHeartsAreZero() {
        // arrange
        OffsetDateTime refillAt = OffsetDateTime.now().plusMinutes(5);
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(0, refillAt)));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(), List.of());

        // act
        LessonQuizStateResponse response = lessonQuizService.getQuizState(userId, lessonId, ACCESS_TOKEN);

        // assert
        assertEquals(LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS, response.status());
        assertEquals(0, response.hearts().heartsRemaining());
        assertFalse(response.canAnswer());
        assertFalse(response.canRestart());
        assertNull(response.currentQuestion());
    }

    @Test
    void getProgressMetadata_ShouldBlockOnlyQuiz_WhenHeartsAreZero() {
        // arrange
        OffsetDateTime refillAt = OffsetDateTime.now().plusMinutes(5);
        List<Map<String, Object>> sections = List.of(
            Map.of("id", LessonFlowConstants.SECTION_INTRO),
            Map.of("id", LessonFlowConstants.SECTION_DEFINITION)
        );
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(0, refillAt)));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of());

        // act
        LessonQuizService.ProgressMetadata response = lessonQuizService.getProgressMetadata(
            userId,
            lessonId,
            sections,
            sections.size(),
            true,
            ACCESS_TOKEN
        );

        // assert
        assertEquals(LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS, response.quizStatus());
        assertEquals(sections.size() + 1, response.totalStops());
        assertEquals(sections.size(), response.completedStops());
        assertEquals(LessonFlowConstants.NEXT_STOP_QUIZ, response.nextStopType());
        assertEquals(0, response.heartsRemaining());
    }

    @Test
    void getHeartsStatus_ShouldRefillToMax_WhenRefillTimeHasPassed() {
        // arrange
        OffsetDateTime expiredRefillAt = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime refreshedRefillAt = OffsetDateTime.now().plusHours(24);
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(2, expiredRefillAt)), List.of(heartsRow(5, refreshedRefillAt)));
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, refreshedRefillAt)));

        // act
        LessonHeartsStatusResponse response = lessonQuizService.getHeartsStatus(userId, ACCESS_TOKEN);

        // assert
        assertEquals(LessonFlowConstants.MAX_HEARTS, response.heartsRemaining());
        assertNotNull(response.heartsRefillAt());

        // verify
        verify(supabaseRestClient).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
    }

    @Test
    void getHeartsStatus_ShouldRescheduleRefillTime_WhenHeartsAreAlreadyFull() {
        // arrange
        OffsetDateTime staleRefillAt = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime normalizedRefillAt = OffsetDateTime.now().plusHours(24);
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, staleRefillAt)), List.of(heartsRow(5, normalizedRefillAt)));
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, normalizedRefillAt)));

        // act
        LessonHeartsStatusResponse response = lessonQuizService.getHeartsStatus(userId, ACCESS_TOKEN);

        // assert
        assertEquals(LessonFlowConstants.MAX_HEARTS, response.heartsRemaining());
        assertNotNull(response.heartsRefillAt());

        // verify
        verify(supabaseRestClient).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
    }

    @Test
    void getProgressMetadata_ShouldRepairMissingRefillSchedule_WhenFullHeartsLoaded() {
        // arrange
        OffsetDateTime repairedRefillAt = OffsetDateTime.now().plusHours(24);
        List<Map<String, Object>> sections = List.of(
            Map.of("id", LessonFlowConstants.SECTION_INTRO),
            Map.of("id", LessonFlowConstants.SECTION_DEFINITION)
        );
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, null)), List.of(heartsRow(5, repairedRefillAt)));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of());
        when(supabaseRestClient.patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, repairedRefillAt)));

        // act
        LessonQuizService.ProgressMetadata response = lessonQuizService.getProgressMetadata(
            userId,
            lessonId,
            sections,
            sections.size(),
            true,
            ACCESS_TOKEN
        );

        // assert
        assertEquals(LessonFlowConstants.QUIZ_STATUS_AVAILABLE, response.quizStatus());
        assertEquals(LessonFlowConstants.MAX_HEARTS, response.heartsRemaining());
        assertNotNull(response.heartsRefillAt());

        // verify
        verify(supabaseRestClient).patchList(eq("user_quiz_hearts"), anyString(), any(), eq(ACCESS_TOKEN), any());
    }

    @Test
    void answerQuestion_ShouldStillPassQuiz_WhenBadgeAchievementAlreadyExists() {
        // arrange
        lesson.put("xp_reward", 25);
        lesson.put("badge_name", "Sigma Starter");
        Map<String, Object> attempt = activeAttempt("attempt-1", 0, 0, 0, 10, "in_progress");
        Map<String, Object> patchedAttempt = activeAttempt("attempt-1", 1, 1, 10, 10, "passed");
        patchedAttempt.put("completed_at", OffsetDateTime.now());
        OffsetDateTime scheduledRefillAt = OffsetDateTime.now().plusHours(8);
        when(supabaseRestClient.getList(eq("lessons"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(lesson));
        when(supabaseRestClient.getList(eq("user_lesson_progress"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()), List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any()))
            .thenReturn(List.of(quiz));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), anyString(), any()))
            .thenReturn(List.of(firstQuestion));
        when(supabaseRestClient.getList(eq("user_quiz_hearts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(heartsRow(5, scheduledRefillAt)));
        when(supabaseRestClient.getList(eq("user_lesson_quiz_attempts"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(attempt));
        when(supabaseRestClient.patchList(eq("user_lesson_quiz_attempts"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(patchedAttempt));
        when(supabaseRestClient.postList(eq("user_quiz_results"), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(Map.of("id", "result-1")));
        when(supabaseRestClient.patchList(eq("user_lesson_progress"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(completedProgressRow()));
        when(supabaseAdminRestClient.getList(eq("user_lesson_rewards"), anyString(), any()))
            .thenReturn(List.of());
        when(supabaseRestClient.postList(eq("user_lesson_rewards"), any(), eq(ACCESS_TOKEN), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "row-level security"))
            .thenReturn(List.of(Map.of("id", "reward-1")));
        when(supabaseAdminRestClient.postList(eq("user_lesson_rewards"), any(), any()))
            .thenReturn(List.of(Map.of("id", "reward-1")));
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(Map.of("id", "profile-1", "reputation_points", 100)));
        when(supabaseRestClient.patchList(eq("profiles"), anyString(), any(), eq(ACCESS_TOKEN), any()))
            .thenReturn(List.of(Map.of("id", "profile-1", "reputation_points", 125)));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any()))
            .thenReturn(List.of(Map.of("id", lessonId.toString(), "completion_count", 2)));
        when(supabaseAdminRestClient.patchList(eq("lessons"), anyString(), any(), any()))
            .thenReturn(List.of(Map.of("id", lessonId.toString(), "completion_count", 3)));
        when(supabaseRestClient.postList(eq("user_achievements"), any(), eq(ACCESS_TOKEN), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "duplicate key value violates unique constraint"));

        // act
        LessonQuizAnswerResponse response = assertDoesNotThrow(() ->
            lessonQuizService.answerQuestion(
                userId,
                lessonId,
                new LessonQuizAnswerRequest("attempt-1", firstQuestionId, Map.of("choiceId", "A")),
                ACCESS_TOKEN
            )
        );

        // assert
        assertTrue(response.correct());
        assertTrue(response.passed());
        assertTrue(response.quizCompleted());
        assertEquals(LessonFlowConstants.QUIZ_STATUS_PASSED, response.status());

        // verify
        verify(supabaseAdminRestClient).postList(eq("user_lesson_rewards"), any(), any());
        verify(supabaseRestClient).postList(eq("user_achievements"), any(), eq(ACCESS_TOKEN), any());
        verify(supabaseAdminRestClient, never()).postList(eq("user_achievements"), any(), any());
    }

    private Map<String, Object> learnerLesson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", lessonId.toString());
        map.put("origin_content", "Origin");
        map.put("definition_content", "Definition");
        map.put("usage_examples", List.of("Usage"));
        map.put("lore_content", "Lore");
        map.put("evolution_content", "Evolution");
        map.put("comparison_content", "Comparison");
        return map;
    }

    private Map<String, Object> heartsRow(int heartsRemaining, OffsetDateTime refillAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId.toString());
        row.put("hearts_remaining", heartsRemaining);
        row.put("refill_at", refillAt);
        return row;
    }

    private Map<String, Object> completedProgressRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("status", "in_progress");
        row.put("progress_percentage", 100);
        row.put("current_section", LessonFlowConstants.SECTION_COMPARISON);
        row.put("last_accessed_at", OffsetDateTime.parse("2026-04-12T00:00:00Z"));
        row.put("created_at", OffsetDateTime.parse("2026-04-12T00:00:00Z"));
        return row;
    }

    private Map<String, Object> activeAttempt(
        String attemptId,
        int currentQuestionIndex,
        int correctCount,
        int earnedScore,
        int maxScore,
        String status
    ) {
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("id", attemptId);
        attempt.put("status", status);
        attempt.put("current_question_index", currentQuestionIndex);
        attempt.put("correct_count", correctCount);
        attempt.put("earned_score", earnedScore);
        attempt.put("max_score", maxScore);
        attempt.put("answers", Map.of("__question_ids", List.of(firstQuestionId, secondQuestionId)));
        attempt.put("updated_at", OffsetDateTime.parse("2026-04-12T00:00:00Z"));
        return attempt;
    }

    private Map<String, Object> multipleChoiceQuestion(String questionId, int orderIndex) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", questionId);
        question.put("quiz_id", quizId);
        question.put("question_type", "multiple_choice");
        question.put("question_text", "Choose the correct answer");
        question.put("correct_answer", "A");
        question.put("points", 10);
        question.put("order_index", orderIndex);
        question.put("options", Map.of("choices", Map.of("A", "Correct", "B", "Wrong")));
        question.put("explanation", "Because A is correct");
        return question;
    }
}

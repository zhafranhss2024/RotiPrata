package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.admin.dto.AdminContentQuizQuestionRequest;
import com.rotiprata.api.admin.dto.AdminContentQuizRequest;
import com.rotiprata.api.content.dto.ContentQuizSubmitRequest;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers content quiz service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class ContentQuizServiceTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;
    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;
    @Mock
    private UserService userService;

    /**
     * Handles suppress warnings.
     */
    // Ensures quiz retrieval requires a non-empty access token.
    @Test
    @SuppressWarnings("unchecked")
    void getContentQuiz_ShouldThrowUnauthorized_WhenAccessTokenMissing() {
        //arrange
        ContentQuizService service = new ContentQuizService(supabaseRestClient, supabaseAdminRestClient, userService);

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.getContentQuiz(UUID.randomUUID(), UUID.randomUUID(), " "));

        //assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        //verify
        verify(supabaseAdminRestClient, never()).getList(eq("content"), any(), any(TypeReference.class));
    }

    /**
     * Handles suppress warnings.
     */
    // Ensures submit computes score and persists quiz attempt rows.
    @Test
    @SuppressWarnings("unchecked")
    void submitContentQuiz_ShouldReturnScoredResult_WhenAnswersAreSubmitted() {
        //arrange
        ContentQuizService service = new ContentQuizService(supabaseRestClient, supabaseAdminRestClient, userService);
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString())));
        when(supabaseAdminRestClient.getList(eq("quizzes"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", "quiz-1", "passing_score", 50)));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", "q1", "correct_answer", "A", "points", 10)));

        //act
        var result = service.submitContentQuiz(userId, contentId,
            new ContentQuizSubmitRequest(Map.of("q1", "A"), 12), "token");

        //assert
        assertEquals(10, result.score());
        assertEquals(10, result.maxScore());
        assertEquals(true, result.passed());

        //verify
        verify(supabaseAdminRestClient).postList(eq("user_quiz_results"), any(), any(TypeReference.class));
    }

    /**
     * Handles suppress warnings.
     */
    // Ensures admin retrieval forbids non-admin users.
    @Test
    @SuppressWarnings("unchecked")
    void getAdminContentQuiz_ShouldThrowForbidden_WhenUserIsNotAdmin() {
        //arrange
        ContentQuizService service = new ContentQuizService(supabaseRestClient, supabaseAdminRestClient, userService);
        UUID userId = UUID.randomUUID();
        when(userService.getRoles(userId, "token")).thenReturn(List.of(AppRole.USER));

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.getAdminContentQuiz(userId, UUID.randomUUID(), "token"));

        //assert
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        //verify
        verify(supabaseAdminRestClient, never()).getList(eq("quizzes"), any(), any(TypeReference.class));
    }

    /**
     * Handles suppress warnings.
     */
    // Ensures replace API archives old quiz and inserts new quiz with normalized questions.
    @Test
    @SuppressWarnings("unchecked")
    void replaceAdminContentQuiz_ShouldReplaceQuestions_WhenValidAdminRequestProvided() {
        //arrange
        ContentQuizService service = new ContentQuizService(supabaseRestClient, supabaseAdminRestClient, userService);
        UUID adminId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        when(userService.getRoles(adminId, "token")).thenReturn(List.of(AppRole.ADMIN));
        when(supabaseAdminRestClient.getList(eq("quizzes"), any(), any(TypeReference.class)))
            .thenReturn(List.of(), List.of(Map.of("id", "quiz-2")));
        when(supabaseAdminRestClient.getList(eq("content"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", contentId.toString(), "title", "Grammar")));
        when(supabaseAdminRestClient.postList(eq("quizzes"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", "quiz-2")));
        when(supabaseAdminRestClient.postList(eq("quiz_questions"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", "qq1")));
        when(supabaseAdminRestClient.getList(eq("quiz_questions"), any(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("id", "qq1", "quiz_id", "quiz-2", "question_text", "Q?", "question_type", "multiple_choice", "options", Map.of("A", "One", "B", "Two"), "correct_answer", "A", "points", 10, "order_index", 0, "created_at", "now")));

        var request = new AdminContentQuizRequest(List.of(
            new AdminContentQuizQuestionRequest("Q?", Map.of("a", "One", "b", "Two"), "a", "exp", 5, 0)
        ));

        //act
        var result = service.replaceAdminContentQuiz(adminId, contentId, request, "token");

        //assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("A", result.get(0).correctAnswer());

        //verify
        verify(supabaseAdminRestClient).postList(eq("quizzes"), any(), any(TypeReference.class));
        verify(supabaseAdminRestClient).postList(eq("quiz_questions"), any(), any(TypeReference.class));
    }
}

package com.rotiprata.api.lesson.service;

import com.rotiprata.api.lesson.dto.LessonHeartsStatusResponse;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerRequest;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.lesson.dto.LessonQuizStateResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defines the lesson quiz service operations exposed to the API layer.
 */
public interface LessonQuizService {

    /**
     * Returns the progress metadata.
     */
    ProgressMetadata getProgressMetadata(
        UUID userId,
        UUID lessonId,
        List<Map<String, Object>> sections,
        int completedSections,
        boolean isEnrolled,
        String accessToken
    );

    /**
     * Returns the quiz state.
     */
    LessonQuizStateResponse getQuizState(UUID userId, UUID lessonId, String accessToken);

    /**
     * Checks whether active lesson quiz.
     */
    boolean hasActiveLessonQuiz(UUID lessonId);

    /**
     * Returns the hearts status.
     */
    LessonHeartsStatusResponse getHeartsStatus(UUID userId, String accessToken);

    /**
     * Handles answer question.
     */
    LessonQuizAnswerResponse answerQuestion(
        UUID userId,
        UUID lessonId,
        LessonQuizAnswerRequest request,
        String accessToken
    );

    /**
     * Handles restart quiz.
     */
    LessonQuizStateResponse restartQuiz(UUID userId, UUID lessonId, String mode, String accessToken);

    /**
     * Implements the lesson quiz service workflows and persistence coordination used by the API layer.
     */
    record ProgressMetadata(
        int totalStops,
        int completedStops,
        String currentStopId,
        int remainingStops,
        String quizStatus,
        int heartsRemaining,
        OffsetDateTime heartsRefillAt,
        String nextStopType
    ) {}
}

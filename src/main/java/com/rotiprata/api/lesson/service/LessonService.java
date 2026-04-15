package com.rotiprata.api.lesson.service;

import com.rotiprata.api.admin.dto.AdminLessonCategoryMoveRequest;
import com.rotiprata.api.admin.dto.AdminLessonCategoryMoveResponse;
import com.rotiprata.api.admin.dto.AdminLessonDraftResponse;
import com.rotiprata.api.admin.dto.AdminPublishLessonResponse;
import com.rotiprata.api.admin.dto.AdminStepSaveRequest;
import com.rotiprata.api.admin.dto.AdminStepSaveResponse;
import com.rotiprata.api.lesson.dto.LessonFeedRequest;
import com.rotiprata.api.lesson.dto.LessonFeedResponse;
import com.rotiprata.api.lesson.dto.LessonHubResponse;
import com.rotiprata.api.lesson.dto.LessonMediaStartLinkRequest;
import com.rotiprata.api.lesson.dto.LessonMediaStartResponse;
import com.rotiprata.api.lesson.dto.LessonMediaStatusResponse;
import com.rotiprata.api.lesson.dto.LessonProgressResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Defines the lesson service operations exposed to the API layer.
 */
public interface LessonService {

    /**
     * Finds the lesson content that best matches the learner question embedding.
     */
    String findRelevantLesson(String accessToken, String question);

    /**
     * Returns the lessons.
     */
    List<Map<String, Object>> getLessons(String accessToken);

    /**
     * Returns the lesson feed.
     */
    LessonFeedResponse getLessonFeed(String accessToken, LessonFeedRequest request);

    /**
     * Returns the lesson hub.
     */
    LessonHubResponse getLessonHub(UUID userId, String accessToken);

    /**
     * Returns the admin lessons.
     */
    List<Map<String, Object>> getAdminLessons(UUID userId, String accessToken);

    /**
     * Returns the admin lesson by id.
     */
    Map<String, Object> getAdminLessonById(UUID userId, UUID lessonId, String accessToken);

    /**
     * Creates the lesson draft.
     */
    AdminLessonDraftResponse createLessonDraft(UUID userId, Map<String, Object> payload, String accessToken);

    /**
     * Saves the lesson step.
     */
    AdminStepSaveResponse saveLessonStep(
        UUID userId,
        UUID lessonId,
        String stepKey,
        AdminStepSaveRequest request,
        String accessToken
    );

    /**
     * Publishes the lesson draft after running the full wizard validation checks.
     */
    AdminPublishLessonResponse publishLessonWithValidation(
        UUID userId,
        UUID lessonId,
        AdminStepSaveRequest request,
        String accessToken
    );

    /**
     * Searches lessons that match the supplied query filters.
     */
    List<Map<String, Object>> searchLessons(String query, String accessToken);

    /**
     * Returns the lesson by id.
     */
    Map<String, Object> getLessonById(UUID lessonId, String accessToken);

    /**
     * Returns the lesson sections.
     */
    List<Map<String, Object>> getLessonSections(UUID lessonId, String accessToken);

    /**
     * Starts the lesson media upload.
     */
    LessonMediaStartResponse startLessonMediaUpload(
        UUID userId,
        UUID lessonId,
        MultipartFile file,
        String accessToken
    );

    /**
     * Starts the lesson media link.
     */
    LessonMediaStartResponse startLessonMediaLink(
        UUID userId,
        UUID lessonId,
        LessonMediaStartLinkRequest request,
        String accessToken
    );

    /**
     * Returns the lesson media status.
     */
    LessonMediaStatusResponse getLessonMediaStatus(
        UUID userId,
        UUID lessonId,
        UUID assetId,
        String accessToken
    );

    /**
     * Creates the lesson.
     */
    Map<String, Object> createLesson(UUID userId, Map<String, Object> payload, String accessToken);

    /**
     * Updates the lesson.
     */
    Map<String, Object> updateLesson(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken);

    /**
     * Moves the lesson to category.
     */
    AdminLessonCategoryMoveResponse moveLessonToCategory(
        UUID userId,
        UUID lessonId,
        AdminLessonCategoryMoveRequest request,
        String accessToken
    );

    /**
     * Deletes the lesson.
     */
    void deleteLesson(UUID userId, UUID lessonId, String accessToken);

    /**
     * Creates the lesson quiz.
     */
    Map<String, Object> createLessonQuiz(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken);

    /**
     * Returns the active lesson quiz questions.
     */
    List<Map<String, Object>> getActiveLessonQuizQuestions(UUID userId, UUID lessonId, String accessToken);

    /**
     * Returns the admin quiz question types.
     */
    List<Map<String, Object>> getAdminQuizQuestionTypes(UUID userId, String accessToken);

    /**
     * Replaces the lesson quiz.
     */
    List<Map<String, Object>> replaceLessonQuiz(
        UUID userId,
        UUID lessonId,
        Map<String, Object> payload,
        String accessToken
    );

    /**
     * Returns the lesson progress.
     */
    LessonProgressResponse getLessonProgress(UUID userId, UUID lessonId, String accessToken);

    /**
     * Completes the lesson section.
     */
    LessonProgressResponse completeLessonSection(UUID userId, UUID lessonId, String sectionId, String accessToken);

    /**
     * Enrolls the lesson.
     */
    void enrollLesson(UUID userId, UUID lessonId, String accessToken);

    /**
     * Updates the lesson progress.
     */
    void updateLessonProgress(UUID userId, UUID lessonId, int progress, String accessToken);

    /**
     * Saves the lesson.
     */
    void saveLesson(UUID userId, UUID lessonId, String accessToken);

    /**
     * Returns the user lesson progress.
     */
    Map<String, Integer> getUserLessonProgress(UUID userId, String accessToken);

    /**
     * Returns the user stats.
     */
    Map<String, Integer> getUserStats(UUID userId, String accessToken);
}

package com.rotiprata.api.lesson.controller;

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
import com.rotiprata.api.lesson.dto.LessonProgressUpdateRequest;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerRequest;
import com.rotiprata.api.lesson.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.lesson.dto.LessonQuizStateResponse;
import com.rotiprata.api.lesson.service.LessonQuizService;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.api.zdto.SectionCompleteResponse;
import com.rotiprata.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class LessonController {
    private final LessonService lessonService;
    private final LessonQuizService lessonQuizService;

    public LessonController(LessonService lessonService, LessonQuizService lessonQuizService) {
        this.lessonService = lessonService;
        this.lessonQuizService = lessonQuizService;
    }

    @GetMapping("/lessons")
    public List<Map<String, Object>> lessons() {
        return lessonService.getLessons(SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/feed")
    public LessonFeedResponse lessonFeed(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "all") String difficulty,
        @RequestParam(defaultValue = "all") String duration,
        @RequestParam(defaultValue = "popular") String sort,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "12") Integer pageSize
    ) {
        LessonFeedRequest request = new LessonFeedRequest(query, difficulty, duration, sort, page, pageSize);
        return lessonService.getLessonFeed(SecurityUtils.getAccessToken(), request);
    }

    @GetMapping("/lessons/hub")
    public LessonHubResponse lessonHub(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getLessonHub(userId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/search-results")
    public List<Map<String, Object>> searchLessonResults(@RequestParam("q") String q) {
        return lessonService.searchLessons(q, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @GetMapping("/lessons/search")
    public List<Map<String, Object>> searchLessons(@RequestParam("q") String q) {
        return searchLessonResults(q);
    }

    @GetMapping("/lessons/{lessonId}")
    public Map<String, Object> lessonById(@PathVariable UUID lessonId) {
        return lessonService.getLessonById(lessonId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/{lessonId}/sections")
    public List<Map<String, Object>> lessonSections(@PathVariable UUID lessonId) {
        return lessonService.getLessonSections(lessonId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/{lessonId}/progress")
    public LessonProgressResponse lessonProgress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getLessonProgress(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/{lessonId}/quiz/state")
    public LessonQuizStateResponse lessonQuizState(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonQuizService.getQuizState(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/quiz/answers")
    public LessonQuizAnswerResponse createLessonQuizAnswer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonQuizAnswerRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonQuizService.answerQuestion(userId, lessonId, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/lessons/{lessonId}/quiz/answer")
    public LessonQuizAnswerResponse answerLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonQuizAnswerRequest request
    ) {
        return createLessonQuizAnswer(jwt, lessonId, request);
    }

    @PostMapping("/lessons/{lessonId}/quiz-attempts")
    public LessonQuizStateResponse createLessonQuizAttempt(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestParam(name = "mode", required = false) String mode
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonQuizService.restartQuiz(userId, lessonId, mode, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/lessons/{lessonId}/quiz/restart")
    public LessonQuizStateResponse restartLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestParam(name = "mode", required = false) String mode
    ) {
        return createLessonQuizAttempt(jwt, lessonId, mode);
    }

    @PutMapping("/lessons/{lessonId}/sections/{sectionId}/completion")
    public SectionCompleteResponse updateLessonSectionCompletion(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable String sectionId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        LessonProgressResponse progress = lessonService.completeLessonSection(
            userId,
            lessonId,
            sectionId,
            SecurityUtils.getAccessToken()
        );
        return new SectionCompleteResponse(progress);
    }

    @Hidden
    @Deprecated
    @PostMapping("/lessons/{lessonId}/sections/{sectionId}/complete")
    public SectionCompleteResponse completeLessonSection(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable String sectionId
    ) {
        return updateLessonSectionCompletion(jwt, lessonId, sectionId);
    }

    @PutMapping("/lessons/{lessonId}/enrollment")
    public void updateLessonEnrollment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.enrollLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/lessons/{lessonId}/enroll")
    public void enrollLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        updateLessonEnrollment(jwt, lessonId);
    }

    @PutMapping("/lessons/{lessonId}/saved")
    public void updateSavedLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.saveLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/lessons/{lessonId}/save")
    public void saveLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        updateSavedLesson(jwt, lessonId);
    }

    @Hidden
    @Deprecated
    @PutMapping("/lessons/{lessonId}/progress")
    public void updateProgress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonProgressUpdateRequest payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.updateLessonProgress(userId, lessonId, payload.progress(), SecurityUtils.getAccessToken());
    }

    @GetMapping("/admin/lessons")
    public List<Map<String, Object>> adminLessons(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getAdminLessons(userId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/admin/lessons/{lessonId}")
    public Map<String, Object> adminLessonById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getAdminLessonById(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lesson-drafts")
    public AdminLessonDraftResponse createLessonDraft(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLessonDraft(userId, payload, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/admin/lessons/draft")
    public AdminLessonDraftResponse createLessonDraftAlias(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) Map<String, Object> payload
    ) {
        return createLessonDraft(jwt, payload);
    }

    @PutMapping("/admin/lesson-drafts/{lessonId}/steps/{stepKey}")
    public AdminStepSaveResponse saveLessonDraftStep(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable String stepKey,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.saveLessonStep(userId, lessonId, stepKey, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PutMapping("/admin/lessons/{lessonId}/draft/step/{stepKey}")
    public AdminStepSaveResponse saveLessonDraftStepAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable String stepKey,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        return saveLessonDraftStep(jwt, lessonId, stepKey, request);
    }

    @PostMapping("/admin/lessons/{lessonId}/publication")
    public AdminPublishLessonResponse publishLesson(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.publishLessonWithValidation(userId, lessonId, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/admin/lessons/{lessonId}/publish")
    public AdminPublishLessonResponse publishLessonAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        return publishLesson(jwt, lessonId, request);
    }

    @PostMapping("/admin/lessons")
    public Map<String, Object> createLesson(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> payload) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLesson(userId, payload, SecurityUtils.getAccessToken());
    }

    @PutMapping("/admin/lessons/{lessonId}")
    public Map<String, Object> updateLesson(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.updateLesson(userId, lessonId, payload, SecurityUtils.getAccessToken());
    }

    @DeleteMapping("/admin/lessons/{lessonId}")
    public void deleteLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.deleteLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/admin/lessons/{lessonId}/category")
    public AdminLessonCategoryMoveResponse moveLessonToCategory(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody AdminLessonCategoryMoveRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.moveLessonToCategory(userId, lessonId, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PutMapping("/admin/lessons/{lessonId}/move-category")
    public AdminLessonCategoryMoveResponse moveLessonToCategoryAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody AdminLessonCategoryMoveRequest request
    ) {
        return moveLessonToCategory(jwt, lessonId, request);
    }

    @GetMapping("/admin/lessons/{lessonId}/quiz")
    public List<Map<String, Object>> getLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getActiveLessonQuizQuestions(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/admin/quiz/question-types")
    public List<Map<String, Object>> getAdminQuizQuestionTypes(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getAdminQuizQuestionTypes(userId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lessons/{lessonId}/quiz")
    public Map<String, Object> createLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLessonQuiz(userId, lessonId, payload, SecurityUtils.getAccessToken());
    }

    @PutMapping("/admin/lessons/{lessonId}/quiz")
    public List<Map<String, Object>> replaceLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.replaceLessonQuiz(userId, lessonId, payload, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lessons/{lessonId}/media-uploads")
    public LessonMediaStartResponse startLessonMediaUpload(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestPart("file") MultipartFile file
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.startLessonMediaUpload(userId, lessonId, file, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/admin/lessons/{lessonId}/media/start")
    public LessonMediaStartResponse startLessonMediaUploadAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestPart("file") MultipartFile file
    ) {
        return startLessonMediaUpload(jwt, lessonId, file);
    }

    @PostMapping("/admin/lessons/{lessonId}/media-link-imports")
    public LessonMediaStartResponse startLessonMediaLink(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonMediaStartLinkRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.startLessonMediaLink(userId, lessonId, request, SecurityUtils.getAccessToken());
    }

    @Hidden
    @Deprecated
    @PostMapping("/admin/lessons/{lessonId}/media/start-link")
    public LessonMediaStartResponse startLessonMediaLinkAlias(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonMediaStartLinkRequest request
    ) {
        return startLessonMediaLink(jwt, lessonId, request);
    }

    @GetMapping("/admin/lessons/{lessonId}/media/{assetId}")
    public LessonMediaStatusResponse lessonMediaStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable UUID assetId
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getLessonMediaStatus(userId, lessonId, assetId, SecurityUtils.getAccessToken());
    }
}

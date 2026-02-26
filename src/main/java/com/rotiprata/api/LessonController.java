package com.rotiprata.api;

import com.rotiprata.api.dto.AdminLessonDraftResponse;
import com.rotiprata.api.dto.AdminPublishLessonResponse;
import com.rotiprata.api.dto.AdminStepSaveRequest;
import com.rotiprata.api.dto.AdminStepSaveResponse;
import com.rotiprata.api.dto.LessonFeedRequest;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.api.dto.LessonHubResponse;
import com.rotiprata.api.dto.LessonProgressResponse;
import com.rotiprata.api.dto.LessonProgressUpdateRequest;
import com.rotiprata.api.dto.LessonQuizAnswerRequest;
import com.rotiprata.api.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.dto.LessonQuizStateResponse;
import com.rotiprata.api.dto.SectionCompleteResponse;
import com.rotiprata.application.LessonService;
import com.rotiprata.application.LessonQuizService;
import com.rotiprata.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/lessons/search")
    public List<Map<String, Object>> searchLessons(@RequestParam("q") String q) {
        return lessonService.searchLessons(q, SecurityUtils.getAccessToken());
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

    @PostMapping("/lessons/{lessonId}/quiz/answer")
    public LessonQuizAnswerResponse answerLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @Valid @RequestBody LessonQuizAnswerRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonQuizService.answerQuestion(userId, lessonId, request, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/quiz/restart")
    public LessonQuizStateResponse restartLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestParam(name = "mode", required = false) String mode
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonQuizService.restartQuiz(userId, lessonId, mode, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/sections/{sectionId}/complete")
    public SectionCompleteResponse completeLessonSection(
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

    @PostMapping("/lessons/{lessonId}/enroll")
    public void enrollLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.enrollLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/save")
    public void saveLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.saveLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

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

    @PostMapping("/admin/lessons/draft")
    public AdminLessonDraftResponse createLessonDraft(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLessonDraft(userId, payload, SecurityUtils.getAccessToken());
    }

    @PutMapping("/admin/lessons/{lessonId}/draft/step/{stepKey}")
    public AdminStepSaveResponse saveLessonDraftStep(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @PathVariable String stepKey,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.saveLessonStep(userId, lessonId, stepKey, request, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lessons/{lessonId}/publish")
    public AdminPublishLessonResponse publishLesson(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody(required = false) AdminStepSaveRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.publishLessonWithValidation(userId, lessonId, request, SecurityUtils.getAccessToken());
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
}
